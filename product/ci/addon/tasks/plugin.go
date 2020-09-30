package tasks

import (
	"context"
	"fmt"
	"io"
	"time"

	"github.com/wings-software/portal/commons/go/lib/exec"
	"github.com/wings-software/portal/commons/go/lib/utils"
	"github.com/wings-software/portal/product/ci/addon/entrypoint"
	pb "github.com/wings-software/portal/product/ci/engine/proto"
	"go.uber.org/zap"
)

//go:generate mockgen -source plugin.go -package=steps -destination mocks/plugin_mock.go PluginTask

const (
	defaultPluginTimeout    int64         = 14400 // 4 hour
	defaultPluginNumRetries int32         = 1
	pluginCmdExitWaitTime   time.Duration = time.Duration(0)
)

var (
	getImageEntrypoint = entrypoint.GetPublicImage
)

// PluginTask represents interface to execute a plugin step
type PluginTask interface {
	Run(ctx context.Context) (int32, error)
}

type pluginTask struct {
	id                string
	displayName       string
	timeoutSecs       int64
	numRetries        int32
	image             string
	log               *zap.SugaredLogger
	procWriter        io.Writer
	cmdContextFactory exec.CmdContextFactory
}

// NewPluginTask creates a plugin step executor
func NewPluginTask(step *pb.UnitStep, log *zap.SugaredLogger, w io.Writer) PluginTask {
	r := step.GetPlugin()
	timeoutSecs := r.GetContext().GetExecutionTimeoutSecs()
	if timeoutSecs == 0 {
		timeoutSecs = defaultPluginTimeout
	}

	numRetries := r.GetContext().GetNumRetries()
	if numRetries == 0 {
		numRetries = defaultPluginNumRetries
	}
	return &pluginTask{
		id:                step.GetId(),
		displayName:       step.GetDisplayName(),
		image:             r.GetImage(),
		timeoutSecs:       timeoutSecs,
		numRetries:        numRetries,
		cmdContextFactory: exec.OsCommandContextGracefulWithLog(log),
		log:               log,
		procWriter:        w,
	}
}

// Executes customer provided run step commands with retries and timeout handling
func (e *pluginTask) Run(ctx context.Context) (int32, error) {
	var err error
	for i := int32(1); i <= e.numRetries; i++ {
		if err = e.execute(ctx, i); err == nil {
			return i, nil
		}
	}
	return e.numRetries, err
}

func (e *pluginTask) execute(ctx context.Context, retryCount int32) error {
	start := time.Now()
	ctx, cancel := context.WithTimeout(ctx, time.Second*time.Duration(e.timeoutSecs))
	defer cancel()

	// TODO: Fetch entrypoint for private plugins
	commands, err := getImageEntrypoint(e.image)
	if err != nil {
		logPluginErr(e.log, "failed to find entrypoint for plugin", e.id, commands, retryCount, start, err)
		return err
	}

	if len(commands) == 0 {
		err := fmt.Errorf("plugin entrypoint is empty")
		logPluginErr(e.log, "entrypoint fetched from remote for plugin is empty", e.id, commands, retryCount, start, err)
		return err
	}

	cmd := e.cmdContextFactory.CmdContextWithSleep(ctx, pluginCmdExitWaitTime, commands[0], commands[1:]...).
		WithStdout(e.procWriter).WithStderr(e.procWriter).WithEnvVarsMap(nil)
	err = cmd.Run()
	if ctxErr := ctx.Err(); ctxErr == context.DeadlineExceeded {
		logPluginErr(e.log, "timeout while executing plugin step", e.id, commands, retryCount, start, ctxErr)
		return ctxErr
	}

	if err != nil {
		logPluginErr(e.log, "error encountered while executing plugin step", e.id, commands, retryCount, start, err)
		return err
	}

	e.log.Infow(
		"Successfully executed plugin",
		"arguments", commands,
		"elapsed_time_ms", utils.TimeSince(start),
	)
	return nil
}

func logPluginErr(log *zap.SugaredLogger, errMsg, stepID string, cmds []string, retryCount int32, startTime time.Time, err error) {
	log.Errorw(
		errMsg,
		"retry_count", retryCount,
		"commands", cmds,
		"elapsed_time_ms", utils.TimeSince(startTime),
		zap.Error(err),
	)
}
