package com.nature.mapper.mxGraph;

import java.util.Date;
import java.util.List;

import org.junit.Test;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.Rollback;

import com.nature.ApplicationTests;
import com.nature.base.util.LoggerUtil;
import com.nature.base.util.Utils;
import com.nature.component.mxGraph.model.MxCell;
import com.nature.component.mxGraph.model.MxGeometry;

import javax.annotation.Resource;

public class MxCellMapperTest extends ApplicationTests {

	@Resource
	private MxCellMapper mxCellMapper;

	Logger logger = LoggerUtil.getLogger();

	@Test
	public void testGetMeCellByMxGraphId() {
		List<MxCell> meCellByMxGraphId = mxCellMapper.getMeCellByMxGraphId("0bb7410706404b4e9bfd96159e58a713");
		logger.info(meCellByMxGraphId.toString());
	}

	@Test
	public void testGetMeCellById() {
		MxCell meCellByMxGraphId = mxCellMapper.getMeCellById("b35003d9c1a9479193398d35835017ec");
		logger.info(meCellByMxGraphId.toString());
	}

	@Test
	@Rollback(true)
	public void testAddMxCell() {
		MxCell mxCell = new MxCell();
		mxCell.setId(Utils.getUUID32());
		mxCell.setCrtDttm(new Date());
		mxCell.setCrtUser("Nature");
		mxCell.setEnableFlag(true);
		mxCell.setLastUpdateUser("Nature");
		mxCell.setLastUpdateDttm(new Date());
		mxCell.setPageId("12");
		mxCell.setParent("3");
		mxCell.setStyle("dafd");
		mxCell.setEdge("edge");
		mxCell.setSource("source");
		mxCell.setTarget("target");
		mxCell.setValue("value");
		mxCell.setVertex("vertex");

		MxGeometry mxGeometry = new MxGeometry();
		mxGeometry.setId(Utils.getUUID32());
		mxGeometry.setCrtDttm(new Date());
		mxGeometry.setCrtUser("Nature");
		mxGeometry.setEnableFlag(true);
		mxGeometry.setLastUpdateUser("Nature");
		mxGeometry.setLastUpdateDttm(new Date());
		mxGeometry.setAs("as");
		mxGeometry.setHeight("111");
		mxGeometry.setRelative("relative");
		mxGeometry.setWidth("111");
		mxGeometry.setX("111");
		mxGeometry.setY("111");
		mxCell.setMxGeometry(mxGeometry);

		int addFlow = mxCellMapper.addMxCell(mxCell);
		logger.info(addFlow + "");
	}

}
