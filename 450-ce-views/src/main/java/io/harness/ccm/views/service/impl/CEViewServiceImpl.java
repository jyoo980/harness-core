package io.harness.ccm.views.service.impl;

import io.harness.ccm.views.dao.CEReportScheduleDao;
import io.harness.ccm.views.dao.CEViewDao;
import io.harness.ccm.views.entities.CEReportSchedule;
import io.harness.ccm.views.entities.CEView;
import io.harness.ccm.views.entities.ViewChartType;
import io.harness.ccm.views.entities.ViewCondition;
import io.harness.ccm.views.entities.ViewField;
import io.harness.ccm.views.entities.ViewFieldIdentifier;
import io.harness.ccm.views.entities.ViewIdCondition;
import io.harness.ccm.views.entities.ViewRule;
import io.harness.ccm.views.entities.ViewState;
import io.harness.ccm.views.entities.ViewTimeGranularity;
import io.harness.ccm.views.entities.ViewTimeRange;
import io.harness.ccm.views.entities.ViewTimeRangeType;
import io.harness.ccm.views.entities.ViewVisualization;
import io.harness.ccm.views.graphql.QLCEView;
import io.harness.ccm.views.graphql.QLCEViewField;
import io.harness.ccm.views.service.CEViewService;
import io.harness.exception.InvalidRequestException;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
public class CEViewServiceImpl implements CEViewService {
  @Inject private CEViewDao ceViewDao;
  @Inject private CEReportScheduleDao ceReportScheduleDao;

  private static final String VIEW_NAME_DUPLICATE_EXCEPTION = "View with given name already exists";
  private static final String VIEW_LIMIT_REACHED_EXCEPTION = "Maximum allowed custom views limit(50) has been reached";
  private static final int VIEW_COUNT = 50;
  @Override
  public CEView save(CEView ceView) {
    validateView(ceView);
    ceView.setViewState(ViewState.DRAFT);
    ceView.setUuid(null);
    ceViewDao.save(ceView);
    return ceView;
  }

  public boolean validateView(CEView ceView) {
    CEView savedCEView = ceViewDao.findByName(ceView.getAccountId(), ceView.getName());
    if (null != savedCEView) {
      throw new InvalidRequestException(VIEW_NAME_DUPLICATE_EXCEPTION);
    }
    List<CEView> views = ceViewDao.findByAccountId(ceView.getAccountId());
    if (views.size() >= VIEW_COUNT) {
      throw new InvalidRequestException(VIEW_LIMIT_REACHED_EXCEPTION);
    }
    modifyCEViewAndSetDefaults(ceView);
    return true;
  }

  private void modifyCEViewAndSetDefaults(CEView ceView) {
    if (ceView.getViewVisualization() == null) {
      ceView.setViewVisualization(ViewVisualization.builder()
                                      .granularity(ViewTimeGranularity.DAY)
                                      .chartType(ViewChartType.STACKED_TIME_SERIES)
                                      .groupBy(ViewField.builder()
                                                   .fieldId("product")
                                                   .fieldName("Product")
                                                   .identifier(ViewFieldIdentifier.COMMON)
                                                   .identifierName(ViewFieldIdentifier.COMMON.getDisplayName())
                                                   .build())
                                      .build());
    }

    if (ceView.getViewTimeRange() == null) {
      ceView.setViewTimeRange(ViewTimeRange.builder().viewTimeRangeType(ViewTimeRangeType.LAST_7).build());
    }

    if (ceView.getViewRules() != null) {
      Set<ViewFieldIdentifier> viewFieldIdentifierSet = new HashSet<>();
      for (ViewRule rule : ceView.getViewRules()) {
        for (ViewCondition condition : rule.getViewConditions()) {
          if (((ViewIdCondition) condition).getViewField().getIdentifier() == ViewFieldIdentifier.CLUSTER) {
            viewFieldIdentifierSet.add(ViewFieldIdentifier.CLUSTER);
          }
          if (((ViewIdCondition) condition).getViewField().getIdentifier() == ViewFieldIdentifier.AWS) {
            viewFieldIdentifierSet.add(ViewFieldIdentifier.AWS);
          }
          if (((ViewIdCondition) condition).getViewField().getIdentifier() == ViewFieldIdentifier.GCP) {
            viewFieldIdentifierSet.add(ViewFieldIdentifier.GCP);
          }
          if (((ViewIdCondition) condition).getViewField().getIdentifier() == ViewFieldIdentifier.CUSTOM) {
            viewFieldIdentifierSet.add(ViewFieldIdentifier.CUSTOM);
          }
        }
      }

      List<ViewFieldIdentifier> viewFieldIdentifierList = new ArrayList<>();
      viewFieldIdentifierList.addAll(viewFieldIdentifierSet);
      ceView.setDataSources(viewFieldIdentifierList);
    }
  }

  @Override
  public CEView get(String uuid) {
    CEView view = ceViewDao.get(uuid);
    if (view.getViewRules() == null) {
      view.setViewRules(Collections.emptyList());
    }
    return view;
  }

  @Override
  public CEView update(CEView ceView) {
    modifyCEViewAndSetDefaults(ceView);
    return ceViewDao.update(ceView);
  }

  @Override
  public boolean delete(String uuid, String accountId) {
    return ceViewDao.delete(uuid, accountId);
  }

  @Override
  public List<QLCEView> getAllViews(String accountId) {
    List<CEView> viewList = ceViewDao.findByAccountId(accountId);
    List<QLCEView> graphQLViewObjList = new ArrayList<>();
    for (CEView view : viewList) {
      List<CEReportSchedule> reportSchedules =
          ceReportScheduleDao.getReportSettingByView(view.getUuid(), view.getAccountId());
      ViewChartType vChartType = null;
      if (view.getViewVisualization() != null) {
        // For DRAFT support, no visualizations have been set at this point
        vChartType = view.getViewVisualization().getChartType();
      }
      ViewField groupBy = view.getViewVisualization().getGroupBy();
      graphQLViewObjList.add(QLCEView.builder()
                                 .id(view.getUuid())
                                 .name(view.getName())
                                 .createdAt(view.getCreatedAt())
                                 .lastUpdatedAt(view.getLastUpdatedAt())
                                 .chartType(vChartType)
                                 .viewType(view.getViewType())
                                 .viewState(view.getViewState())
                                 .groupBy(QLCEViewField.builder()
                                              .fieldId(groupBy.getFieldId())
                                              .fieldName(groupBy.getFieldName())
                                              .identifier(groupBy.getIdentifier())
                                              .identifierName(groupBy.getIdentifier().getDisplayName())
                                              .build())
                                 .timeRange(view.getViewTimeRange().getViewTimeRangeType())
                                 .dataSources(view.getDataSources())
                                 .isReportScheduledConfigured(!reportSchedules.isEmpty())
                                 .build());
    }
    return graphQLViewObjList;
  }
}
