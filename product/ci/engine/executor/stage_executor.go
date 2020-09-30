package executor

import (
	"context"
	"encoding/base64"
	"fmt"

	"github.com/golang/protobuf/proto"
	"github.com/wings-software/portal/product/ci/engine/output"
	pb "github.com/wings-software/portal/product/ci/engine/proto"
	"go.uber.org/zap"
)

// StageExecutor represents an interface to execute a stage
type StageExecutor interface {
	Run() error
}

// NewStageExecutor creates a stage executor
func NewStageExecutor(encodedStage, tmpFilePath string, debug bool,
	log *zap.SugaredLogger) StageExecutor {
	o := make(output.StageOutput)
	unitExecutor := NewUnitExecutor(tmpFilePath, log)
	parallelExecutor := NewParallelExecutor(tmpFilePath, log)
	return &stageExecutor{
		encodedStage:     encodedStage,
		tmpFilePath:      tmpFilePath,
		debug:            debug,
		stageOutput:      o,
		unitExecutor:     unitExecutor,
		parallelExecutor: parallelExecutor,
		log:              log,
	}
}

type stageExecutor struct {
	log              *zap.SugaredLogger
	tmpFilePath      string             // File path to store generated temporary files
	encodedStage     string             // Stage in base64 encoded format
	debug            bool               // If true, enables debug mode for checking run step logs by not exiting lite-engine
	stageOutput      output.StageOutput // Stage output will store the output of steps in a stage
	unitExecutor     UnitExecutor
	parallelExecutor ParallelExecutor
}

// Executes steps in a stage
func (e *stageExecutor) Run() error {
	ctx := context.Background()
	if e.debug == true {
		defer func() { select {} }()
	}

	execution, err := e.decodeStage(e.encodedStage)
	if err != nil {
		return err
	}

	// Execute steps in a stage & cleans up the resources allocated for a step after its completion.
	cleanupOnly := false
	var stepExecErr error
	for _, step := range execution.GetSteps() {
		if !cleanupOnly {
			stepExecErr = e.executeStep(ctx, step, execution.GetAccountId())
			if stepExecErr != nil {
				cleanupOnly = true
			}
		}
		e.cleanupStep(ctx, step)
	}
	return stepExecErr
}

// executeStep method executes a unit step or a parallel step
func (e *stageExecutor) executeStep(ctx context.Context, step *pb.Step, accountID string) error {
	switch x := step.GetStep().(type) {
	case *pb.Step_Unit:
		stepOutput, err := e.unitExecutor.Run(ctx, step.GetUnit(), e.stageOutput, accountID)
		if err != nil {
			return err
		}
		e.stageOutput[step.GetUnit().GetId()] = stepOutput
	case *pb.Step_Parallel:
		stepOutputByID, err := e.parallelExecutor.Run(ctx, step.GetParallel(), e.stageOutput, accountID)
		if err != nil {
			return err
		}
		for stepID, stepOutput := range stepOutputByID {
			e.stageOutput[stepID] = stepOutput
		}
	default:
		return fmt.Errorf("Step has unexpected type %T", x)
	}
	return nil
}

// cleanupStep method terminates any resource present for the step e.g. addon container
func (e *stageExecutor) cleanupStep(ctx context.Context, step *pb.Step) error {
	var err error
	switch x := step.GetStep().(type) {
	case *pb.Step_Unit:
		err = e.unitExecutor.Cleanup(ctx, step.GetUnit())
	case *pb.Step_Parallel:
		err = e.parallelExecutor.Cleanup(ctx, step.GetParallel())
	default:
		err = fmt.Errorf("Step has unexpected type %T", x)
	}
	return err
}

func (e *stageExecutor) decodeStage(encodedStage string) (*pb.Execution, error) {
	decodedStage, err := base64.StdEncoding.DecodeString(e.encodedStage)
	if err != nil {
		e.log.Errorw("Failed to decode stage", "encoded_stage", e.encodedStage, zap.Error(err))
		return nil, err
	}

	execution := &pb.Execution{}
	err = proto.Unmarshal(decodedStage, execution)
	if err != nil {
		e.log.Errorw("Failed to deserialize stage", "decoded_stage", decodedStage, zap.Error(err))
		return nil, err
	}

	e.log.Infow("Deserialized execution", "execution", execution.String())
	return execution, nil
}

// ExecuteStage executes a stage of the pipeline
func ExecuteStage(input, tmpFilePath string, debug bool, log *zap.SugaredLogger) error {
	executor := NewStageExecutor(input, tmpFilePath, debug, log)
	if err := executor.Run(); err != nil {
		log.Errorw(
			"error while executing steps in a stage",
			"embedded_stage", input,
			zap.Error(err),
		)
		return err
	}
	return nil
}
