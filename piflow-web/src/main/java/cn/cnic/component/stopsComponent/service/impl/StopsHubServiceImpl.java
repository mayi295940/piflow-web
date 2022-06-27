package cn.cnic.component.stopsComponent.service.impl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import cn.cnic.common.constant.MessageConfig;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;

import cn.cnic.base.utils.FileUtils;
import cn.cnic.base.utils.JsonUtils;
import cn.cnic.base.utils.PageHelperUtils;
import cn.cnic.base.utils.ReturnMapUtils;
import cn.cnic.base.utils.UUIDUtils;
import cn.cnic.common.Eunm.StopsHubState;
import cn.cnic.component.process.entity.Process;
import cn.cnic.component.stopsComponent.domain.StopsComponentDomain;
import cn.cnic.component.stopsComponent.domain.StopsHubDomain;
import cn.cnic.component.stopsComponent.entity.StopsComponent;
import cn.cnic.component.stopsComponent.entity.StopsComponentGroup;
import cn.cnic.component.stopsComponent.entity.StopsHub;
import cn.cnic.component.stopsComponent.service.IStopsHubService;
import cn.cnic.component.stopsComponent.utils.StopsComponentGroupUtils;
import cn.cnic.component.stopsComponent.utils.StopsComponentUtils;
import cn.cnic.component.stopsComponent.utils.StopsHubUtils;
import cn.cnic.third.service.IStop;
import cn.cnic.third.vo.stop.StopsHubVo;
import cn.cnic.third.vo.stop.ThirdStopsComponentVo;

@Service
public class StopsHubServiceImpl implements IStopsHubService {

    private final StopsComponentDomain stopsComponentDomain;
    private final StopsHubDomain stopsHubDomain;
    private final IStop stopImpl;

    @Autowired
    public StopsHubServiceImpl(StopsComponentDomain stopsComponentDomain,
                               StopsHubDomain stopsHubDomain,
                               IStop stopImpl) {
        this.stopsComponentDomain = stopsComponentDomain;
        this.stopsHubDomain = stopsHubDomain;
        this.stopImpl = stopImpl;
    }

    @Override
    public String uploadStopsHubFile(String username, MultipartFile file) {

        //call piflow server api: plunin/path
        String stopsHubPath = stopImpl.getStopsHubPath();

        //upload jar file to plugin path
        String stopsHubName = file.getOriginalFilename();

        if (StringUtils.isBlank(username)) {
            return ReturnMapUtils.setFailedMsgRtnJsonStr("Illegal users");
        }
        if (file.isEmpty()) {
            return ReturnMapUtils.setFailedMsgRtnJsonStr("Upload failed, please try again later");
        }
        Map<String, Object> uploadMap = FileUtils.uploadRtnMap(file, stopsHubPath, stopsHubName);
        if (null == uploadMap || uploadMap.isEmpty()) {
            return ReturnMapUtils.setFailedMsgRtnJsonStr("Upload failed, please try again later");
        }
        Integer code = (Integer) uploadMap.get("code");
        if (500 == code) {
            return ReturnMapUtils.setFailedMsgRtnJsonStr("failed to upload file");
        }

        //save stopsHub to db
        StopsHub stopsHub = StopsHubUtils.stopsHubNewNoId(username);
        stopsHub.setId(UUIDUtils.getUUID32());
        stopsHub.setJarName(stopsHubName);
        stopsHub.setJarUrl(stopsHubPath + stopsHubName);
        stopsHub.setStatus(StopsHubState.UNMOUNT);
        stopsHubDomain.addStopHub(stopsHub);
        return ReturnMapUtils.setSucceededMsgRtnJsonStr("successful jar upload");
    }

    @Override
    public String mountStopsHub(String username, Boolean isAdmin, String id) {

        StopsHub stopsHub = stopsHubDomain.getStopsHubById(username, isAdmin, id);
//        if(stopsHub.getStatus() == StopsHubState.MOUNT){
//            return ReturnMapUtils.setFailedMsgRtnJsonStr("StopsHub have been Mounted already!");
//        }
        StopsHubVo stopsHubVo = stopImpl.mountStopsHub(stopsHub.getJarName());
        if (stopsHubVo == null) {
            return ReturnMapUtils.setFailedMsgRtnJsonStr("Mount failed, please try again later");
        }

        stopsHub.setMountId(stopsHubVo.getMountId());
        stopsHub.setStatus(StopsHubState.MOUNT);
        stopsHub.setLastUpdateUser(username);
        stopsHub.setLastUpdateDttm(new Date());
        stopsHubDomain.updateStopHub(stopsHub);

        //TODO: remove stops and groups from db
        List<ThirdStopsComponentVo> stops = stopsHubVo.getStops();
        List<String> groupNameList = new ArrayList<>();
        Map<String, StopsComponentGroup> stopsComponentGroupMap = new HashMap<>();
        for (ThirdStopsComponentVo s : stops) {
            groupNameList.addAll(Arrays.asList(s.getGroups().split(",")));
        }
        List<String> distinctGroupNameList = groupNameList.stream().distinct().collect(Collectors.toList());
        List<StopsComponentGroup> stopsComponentGroupList = stopsComponentDomain.getStopGroupByGroupNameList(distinctGroupNameList);
        for (StopsComponentGroup sGroup : stopsComponentGroupList) {
            stopsComponentGroupMap.put(sGroup.getGroupName(), sGroup);
        }
        for (ThirdStopsComponentVo s : stops) {

            List<String> stopGroupNameList = Arrays.asList(s.getGroups().split(","));
            for (String groupName : stopGroupNameList) {

                StopsComponentGroup stopsComponentGroup = stopsComponentGroupMap.get(groupName);
                if (stopsComponentGroup == null) {
                    // add group into db
                    stopsComponentGroup = StopsComponentGroupUtils.stopsComponentGroupNewNoId(username);
                    stopsComponentGroup.setId(UUIDUtils.getUUID32());
                    stopsComponentGroup.setGroupName(groupName);
                    stopsComponentDomain.addStopsComponentGroup(stopsComponentGroup);
                    stopsComponentGroupMap.put(groupName, stopsComponentGroup);
                }
            }

            //add stop into db
            List<StopsComponentGroup> stopGroupByName = new ArrayList<>();
            for (String groupName : stopGroupNameList) {
                stopGroupByName.add(stopsComponentGroupMap.get(groupName));
            }
            StopsComponent stopsComponent = stopsComponentDomain.getStopsComponentByBundle(s.getBundle());
            if (stopsComponent == null) {
                stopsComponent = StopsComponentUtils.thirdStopsComponentVoToStopsTemplate(username, s, stopGroupByName);
                stopsComponentDomain.addStopsComponentAndChildren(stopsComponent);
                //stopsComponentMapper.insertStopsComponent(stopsComponent);

            } else {//update stop group
                //stopsComponent.setStopGroupList(stopGroupByName);
                //TODO: Update group info
            }
            //add stop and group relationship
            for (StopsComponentGroup sGroup : stopGroupByName) {
                stopsComponentDomain.deleteGroupCorrelationByGroupIdAndStopId(sGroup.getId(), stopsComponent.getId());
                stopsComponentDomain.insertAssociationGroupsStopsTemplate(sGroup.getId(), stopsComponent.getId());
            }

        }

        return ReturnMapUtils.setSucceededMsgRtnJsonStr("Mount successful");
    }

    @Override
    public String unmountStopsHub(String username, Boolean isAdmin, String id) {

        StopsHub stopsHub = stopsHubDomain.getStopsHubById(username, isAdmin, id);
        if (stopsHub.getStatus() == StopsHubState.UNMOUNT) {
            return ReturnMapUtils.setFailedMsgRtnJsonStr("StopsHub have been UNMounted already!");
        }

        StopsHubVo stopsHubVo = stopImpl.unmountStopsHub(stopsHub.getMountId());
        if (stopsHubVo == null) {
            return ReturnMapUtils.setFailedMsgRtnJsonStr("UNMount failed, please try again later");
        }
        stopsHub.setMountId(stopsHubVo.getMountId());
        stopsHub.setStatus(StopsHubState.UNMOUNT);
        stopsHub.setLastUpdateUser(username);
        stopsHub.setLastUpdateDttm(new Date());
        stopsHubDomain.updateStopHub(stopsHub);

        //TODO: remove stops and groups into db,
        List<ThirdStopsComponentVo> stops = stopsHubVo.getStops();
        for (ThirdStopsComponentVo s : stops) {
            StopsComponent stopsComponent = stopsComponentDomain.getStopsComponentByBundle(s.getBundle());
            stopsComponentDomain.deleteStopsComponent(stopsComponent);
        }

        return ReturnMapUtils.setSucceededMsgRtnJsonStr("Mount successful");
    }

    /**
     * stopsHubListPage
     *
     * @param username username
     * @param isAdmin  is admin
     * @param pageNo   Number of pages
     * @param limit    Number each page
     * @param param    Search content
     * @return
     */
    @Override
    public String stopsHubListPage(String username, Boolean isAdmin, Integer pageNo, Integer limit, String param) {
        if (null == pageNo || null == limit) {
            return ReturnMapUtils.setFailedMsgRtnJsonStr(MessageConfig.ERROR_MSG());
        }
        Page<Process> page = PageHelper.startPage(pageNo, limit, "crt_dttm desc");
        stopsHubDomain.getStopsHubListParam(username, isAdmin, param);
        Map<String, Object> rtnMap = ReturnMapUtils.setSucceededMsg(MessageConfig.SUCCEEDED_MSG());
        return PageHelperUtils.setLayTableParamRtnStr(page, rtnMap);
    }

    /**
     * del stopsHub
     *
     * @param username username
     * @param id       id
     * @return json
     */
    @Override
    public String delStopsHub(String username, Boolean isAdmin, String id) {
        if (StringUtils.isBlank(id)) {
            return ReturnMapUtils.setFailedMsgRtnJsonStr("id is null");
        }
        StopsHub stopsHubById = stopsHubDomain.getStopsHubById(username, isAdmin, id);
        if (null == stopsHubById) {
            return ReturnMapUtils.setFailedMsgRtnJsonStr("no data");
        }
        StopsHubState status = stopsHubById.getStatus();
        if (StopsHubState.MOUNT == status) {
            return ReturnMapUtils.setFailedMsgRtnJsonStr("The status is MOUNT and deletion is prohibited ");
        }
        int i = stopsHubDomain.deleteStopsHubById(username, id);
        if (i <= 0) {
            return ReturnMapUtils.setFailedMsgRtnJsonStr("delete failed");
        }
        return ReturnMapUtils.setSucceededMsgRtnJsonStr(MessageConfig.SUCCEEDED_MSG());
    }

}
