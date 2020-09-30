package parser

import (
	"context"
	"fmt"
	"net/http"
	"strings"
	"time"

	"github.com/drone/go-scm/scm"
	"github.com/drone/go-scm/scm/driver/bitbucket"
	"github.com/drone/go-scm/scm/driver/gitea"
	"github.com/drone/go-scm/scm/driver/github"
	"github.com/drone/go-scm/scm/driver/gitlab"
	"github.com/drone/go-scm/scm/driver/gogs"
	"github.com/drone/go-scm/scm/driver/stash"
	"github.com/wings-software/portal/commons/go/lib/utils"
	"github.com/wings-software/portal/product/ci/scm/converter"
	pb "github.com/wings-software/portal/product/ci/scm/proto"
	"go.uber.org/zap"
	"google.golang.org/grpc/codes"
	"google.golang.org/grpc/status"
)

const (
	gogsURI       = "api.gogs.com"
	giteaURI      = "api.gitea.com"
	defaultMethod = "POST"
	defaultPath   = "/"
)

// ParseWebhook parses webhook payload into protobuf ParseWebhookResponse format
func ParseWebhook(ctx context.Context, in *pb.ParseWebhookRequest,
	log *zap.SugaredLogger) (*pb.ParseWebhookResponse, error) {
	start := time.Now()
	webhook, err := parseRequest(in)
	if err != nil {
		log.Errorw(
			"Failed to parse input webhook payload",
			"input", in.String(),
			"elapsed_time_ms", utils.TimeSince(start),
			zap.Error(err),
		)
		return nil, err
	}

	switch event := webhook.(type) {
	case *scm.PullRequestHook:
		pr, err := converter.ConvertPRHook(event)
		if err != nil {
			return nil, err
		}
		log.Infow("Successfully parsed pr webhook", "elapsed_time_ms", utils.TimeSince(start))
		return &pb.ParseWebhookResponse{
			Hook: &pb.ParseWebhookResponse_Pr{
				Pr: pr,
			},
		}, nil
	case *scm.PushHook:
		push, err := converter.ConvertPushHook(event)
		if err != nil {
			return nil, err
		}
		log.Infow("Successfully parsed push webhook", "elapsed_time_ms", utils.TimeSince(start))
		return &pb.ParseWebhookResponse{
			Hook: &pb.ParseWebhookResponse_Push{
				Push: push,
			},
		}, nil
	default:
		log.Errorw(
			"Unsupported webhook event",
			"event", event,
			"elapsed_time_ms", utils.TimeSince(start),
			zap.Error(err),
		)
		return nil, status.Errorf(codes.InvalidArgument,
			fmt.Sprintf("Unsupported webhook event %v", event))
	}
}

// parseRequest parses incoming request and convert it to scm.Webhook
func parseRequest(in *pb.ParseWebhookRequest) (scm.Webhook, error) {
	body := strings.NewReader(in.GetBody())
	r, err := http.NewRequest(defaultMethod, defaultPath, body)
	if err != nil {
		return nil, err
	}
	for _, field := range in.GetHeader().GetFields() {
		for _, v := range field.GetValues() {
			r.Header.Set(field.GetKey(), v)
		}
	}

	c, err := getClient(in.GetProvider())
	if err != nil {
		return nil, err
	}

	h, err := c.Webhooks.Parse(r, func(scm.Webhook) (string, error) {
		return in.GetSecret(), nil
	})
	if err != nil {
		return nil, err
	}
	return h, nil
}

// getClient returns appropriate scm client.
func getClient(p pb.GitProvider) (*scm.Client, error) {
	switch p {
	case pb.GitProvider_BITBUCKET:
		return bitbucket.NewDefault(), nil
	case pb.GitProvider_GITEA:
		return gitea.New(giteaURI)
	case pb.GitProvider_GITHUB:
		return github.NewDefault(), nil
	case pb.GitProvider_GITLAB:
		return gitlab.NewDefault(), nil
	case pb.GitProvider_GOGS:
		return gogs.New(gogsURI)
	case pb.GitProvider_STASH:
		return stash.NewDefault(), nil
	default:
		return nil, status.Errorf(codes.InvalidArgument,
			fmt.Sprintf("Unsupported git provider %s", p.String()))
	}
}
