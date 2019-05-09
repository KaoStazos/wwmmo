package au.com.codeka.warworlds.model;

import android.content.Context;

import java.util.TreeMap;

import au.com.codeka.common.model.Design;
import au.com.codeka.common.model.DesignKind;
import au.com.codeka.common.protobuf.Messages;
import au.com.codeka.warworlds.StyledDialog;
import au.com.codeka.warworlds.api.ApiRequest;
import au.com.codeka.warworlds.api.RequestManager;

public class BuildManager {
  public static final BuildManager i = new BuildManager();

  private TreeMap<String, Integer> buildingDesignCounts;

  private BuildManager() {
    buildingDesignCounts = new TreeMap<>();
  }

  public void setup(Messages.EmpireBuildingStatistics empire_building_statistics_pb) {
    buildingDesignCounts.clear();
    for (Messages.EmpireBuildingStatistics.DesignCount design_count_pb
        : empire_building_statistics_pb.getCountsList()) {
      buildingDesignCounts.put(design_count_pb.getDesignId(), design_count_pb.getNumBuildings());
    }
  }

  public int getTotalBuildingsInEmpire(String designId) {
    if (buildingDesignCounts.get(designId) == null) {
      return 0;
    } else {
      return buildingDesignCounts.get(designId);
    }
  }

  public void updateNotes(final String buildRequestKey, final String notes) {
    Messages.BuildRequest build = Messages.BuildRequest.newBuilder()
        .setKey(buildRequestKey).setNotes(notes).build();
    RequestManager.i.sendRequest(new ApiRequest.Builder("buildqueue", "PUT").body(build).build());
  }

  public void build(
      Context context, Colony colony, Design design, Building existingBuilding, int count,
      boolean accelerateImmediately) {
    Messages.BuildRequest.BUILD_KIND kind;
    if (design.getDesignKind() == DesignKind.BUILDING) {
      kind = Messages.BuildRequest.BUILD_KIND.BUILDING;
    } else {
      kind = Messages.BuildRequest.BUILD_KIND.SHIP;
    }

    Messages.BuildRequest build_request_pb =
        Messages.BuildRequest.newBuilder().setBuildKind(kind).setStarKey(colony.getStarKey())
            .setColonyKey(colony.getKey()).setEmpireKey(colony.getEmpireKey())
            .setDesignName(design.getID()).setCount(count)
            .setExistingBuildingKey(existingBuilding == null ? "" : existingBuilding.getKey())
            .setAccelerateImmediately(accelerateImmediately)
            .build();

    build(context, build_request_pb);
  }

  public void build(
      Context context, Colony colony, Design design, int fleetID, int count, String upgradeID,
      boolean accelerateImmediately) {
    Messages.BuildRequest build_request_pb =
        Messages.BuildRequest.newBuilder().setBuildKind(Messages.BuildRequest.BUILD_KIND.SHIP)
            .setStarKey(colony.getStarKey()).setColonyKey(colony.getKey())
            .setEmpireKey(colony.getEmpireKey()).setDesignName(design.getID())
            .setAccelerateImmediately(accelerateImmediately)
            .setExistingFleetId(fleetID).setCount(count).setUpgradeId(upgradeID).build();

    build(context, build_request_pb);
  }

  private void build(final Context context, final Messages.BuildRequest buildRequestPb) {
    RequestManager.i.sendRequest(new ApiRequest.Builder("buildqueue", "POST").body(buildRequestPb)
        .completeCallback(new ApiRequest.CompleteCallback() {
          @Override
          public void onRequestComplete(ApiRequest request) {
            Messages.BuildRequest buildRequestPb = request.body(Messages.BuildRequest.class);
            BuildRequest br = new BuildRequest();
            br.fromProtocolBuffer(buildRequestPb);
            StarManager.i.refreshStar(Integer.parseInt(buildRequestPb.getStarKey()));
          }
        }).errorCallback(new ApiRequest.ErrorCallback() {
          @Override
          public void onRequestError(ApiRequest request, Messages.GenericError error) {
            try {
              new StyledDialog.Builder(context)
                  .setTitle("Cannot Build")
                  .setMessage(error.getErrorMessage())
                  .setPositiveButton("Close", true, null)
                  .create().show();
            } catch (Exception e) {
              // we can get a WindowManager.BadTokenException here if the activity has finished, we
              // should probably do something about it but it's kinda too late...
            }
          }
        }).build());
  }
}
