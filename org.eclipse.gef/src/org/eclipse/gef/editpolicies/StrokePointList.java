package org.eclipse.gef.editpolicies;
/*
 * Licensed Material - Property of IBM
 * (C) Copyright IBM Corp. 2001, 2002 - All Rights Reserved.
 * US Government Users Restricted Rights - Use, duplication or disclosure
 * restricted by GSA ADP Schedule Contract with IBM Corp.
 */
import java.awt.BasicStroke;
import java.awt.Shape;
import java.awt.geom.*;

import org.eclipse.draw2d.geometry.Point;
import org.eclipse.draw2d.geometry.PointList;

/**
 * @author hudsonr
 * @since 2.0 */
public class StrokePointList {

static float segment [] = new float[6];

static PointList strokeList(PointList list, int offset) {
	GeneralPath path = new GeneralPath(GeneralPath.WIND_NON_ZERO);

	Point p = list.getPoint(0);
	path.moveTo(p.x, p.y);
	for (int i = 1; i < list.size(); i++)
		path.lineTo((p = list.getPoint(i)).x, p.y);
	BasicStroke stroke = new BasicStroke(
		offset * 2,
		BasicStroke.CAP_SQUARE,
		BasicStroke.JOIN_ROUND, 10.0f);
	Shape stroked = stroke.createStrokedShape(path);
	Area area = new Area(stroked);
	PathIterator iter = area.getPathIterator(null, 10.0f);

	PointList currentSegment = null;
	PointList result = null;
	int largestSegmentSize = 0;

	while (!iter.isDone()) {
		if (currentSegment == null)
			currentSegment = new PointList(list.size() * 2);
		int type = iter.currentSegment(segment);
		currentSegment.addPoint(Math.round(segment[0]), Math.round(segment[1]));
		iter.next();
		if (type == PathIterator.SEG_CLOSE) {
			if (currentSegment.size() > largestSegmentSize) {
				result = currentSegment;
				largestSegmentSize = currentSegment.size();
				currentSegment = null;
			}
		}
	}

	return result;
}

}
