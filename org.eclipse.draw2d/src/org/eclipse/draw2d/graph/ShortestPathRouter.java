/*******************************************************************************
 * Copyright (c) 2004 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 * 
 * Contributors:
 * IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.draw2d.graph;

import java.util.*;

import org.eclipse.draw2d.Bendpoint;
import org.eclipse.draw2d.PositionConstants;
import org.eclipse.draw2d.geometry.Point;
import org.eclipse.draw2d.geometry.Rectangle;

/**
 * Bends a collection of {@link Path Paths} around rectangular obstacles. This class
 * maintains a list of paths and obstacles. Updates can be made to the paths and/or
 * obstacles, and then an incremental solve can be invoked.
 * <P>
 * The algorithm will attempt to find the shortest non-intersecting path between each
 * path's start and end points. Once all paths have been found, they will be offset based
 * on how many paths bend around the same corner of each obstacle.
 * <P>
 * The worst-case performance of this algorithm is p * n^2, where p is the number of
 * paths, and n is the number of obstacles.
 * <P>
 * This class is not intended to be subclassed.
 * @author Whitney Sorenson
 * @since 3.0
 */
public class ShortestPathRouter {

/**
 * A stack of Paths.
 */
static class PathStack extends ArrayList {

	Path pop() {
		return (Path)remove(size() - 1);
	}
	
	void push(Path path) {
		add(path);
	}

}

/**
 * The number of times to grow obstacles and test for intersections. This is a tradeoff
 * between performance and quality of output.
 */
private static final int NUM_GROW_PASSES = 2;

private List allObstacles, paths, allPaths, orderedPaths, subPaths;
private boolean growPassChangedObstacles;
private Map pathsToChildPaths;

private PathStack stack = new PathStack();

/**
 * Creates a new shortest path routing.
 */
public ShortestPathRouter() {
	subPaths = new ArrayList();
	paths = new ArrayList();
	orderedPaths = new ArrayList();
	allPaths = new ArrayList();
	allObstacles = new ArrayList();
	pathsToChildPaths = new HashMap();
}

/**
 * Adds an obstacle with the given bounds to the obstacles. 
 * 
 * @param rect the bounds of this obstacle
 */
public void addObstacle(Rectangle rect) {
	Obstacle obs = new Obstacle(rect);
	allObstacles.add(obs);
	testPaths(obs);
}

/**
 * Adds a path to the routing.
 * 
 * @param path the path to add.
 */
public void addPath(Path path) {
	allPaths.add(path);
	paths.add(path);
}

/**
 * Fills the point lists of the Paths to the correct bent points.
 */
private void bendPaths() {
	for (int i = 0; i < orderedPaths.size(); i++) {
		Path path = (Path) orderedPaths.get(i);
		Segment segment = null;
		path.points.addPoint(new Point(path.start.x, path.start.y));
		for (int v = 0; v < path.grownSegments.size(); v++) {
			segment = (Segment) path.grownSegments.get(v);
			Vertex vertex = segment.end;

			if (vertex != null && v < path.grownSegments.size() - 1) {
				if (vertex.type == Vertex.INNIE) {
					vertex.count++;
					path.points.addPoint(vertex.bend(vertex.count));
				} else {
					path.points.addPoint(vertex.bend(vertex.totalCount));
					vertex.totalCount--;
				}
			}
		}
		path.points.addPoint(new Point(path.end.x, path.end.y));
	}
}

/**
 * Checks a vertex to see if its offset should shrink
 * @param vertex the vertex to check
 */
private void checkVertexForIntersections(Vertex vertex) {
	if (vertex.shortestDistance != 0 || vertex.shortestDistanceChecked)
		return;
	int sideLength, x, y;
	
	sideLength = 2 * (vertex.totalCount * Vertex.BEND_OFFSET) + 1;
	
	if ((vertex.positionOnObstacle & PositionConstants.NORTH) > 0) 
		y = vertex.y - sideLength;
	else 
		y = vertex.y;					
	if ((vertex.positionOnObstacle & PositionConstants.EAST) > 0)
		x = vertex.x;
	else
		x = vertex.x - sideLength;
	
	Rectangle r = new Rectangle(x, y, sideLength, sideLength);
	
	int xDist, yDist;
	
	for (int o = 0; o < allObstacles.size(); o++) {
		Obstacle obs = (Obstacle)allObstacles.get(o);
		if (obs != vertex.obs && r.intersects(obs)) {
			int pos = obs.getPosition(vertex);
			if (pos == 0)
				continue;
		
			if ((pos & PositionConstants.NORTH) > 0)
				//	 use top
				yDist = obs.y - vertex.y;						
			else
				// use bottom
				yDist = vertex.y - obs.bottom() + 1;
			if ((pos & PositionConstants.EAST) > 0) 
				//	 use right
				xDist = vertex.x - obs.right() + 1;
			else 
				//	 use left 
				xDist = obs.x - vertex.x;
		
			if (Math.max(xDist, yDist) < vertex.shortestDistance 
					|| vertex.shortestDistance == 0) {
				vertex.shortestDistance = Math.max(xDist, yDist);
				vertex.updateOffset();
			}
			
		}
	}
	
	vertex.shortestDistanceChecked = true;
}

/**
 * Checks all vertices along paths for intersections
 */
private void checkVertexIntersections() {
	for (int i = 0; i < paths.size(); i++) {
		Path path = (Path)paths.get(i);
		
		for (int s = 0; s < path.segments.size() - 1; s++) {
			Vertex vertex = ((Segment)path.segments.get(s)).end;
			checkVertexForIntersections(vertex);
		}
	}
}

/**
 * Counts how many paths are on given vertices in order to increment their total count.
 */
private void countVertices() {
	for (int i = 0; i < paths.size(); i++) {
		Path path = (Path) paths.get(i);
		for (int v = 0; v < path.segments.size() - 1; v++)
			((Segment)path.segments.get(v)).end.totalCount++;
	}
}

/**
 * Dirties the paths that are on the given vertex
 * @param vertex the vertex that has the paths
 */
private void dirtyPathsOn(Vertex vertex) {
	List paths = vertex.paths;
	if (paths != null) {
		for (int i = 0; i < paths.size(); i++)
			((Path)paths.get(i)).isDirty = true;
	}
}

/**
 * Returns the closest vertex to the given segment.
 * @param v1 the first vertex
 * @param v2 the second vertex
 * @param segment the segment
 * @return v1, or v2 whichever is closest to the segment
 */
private Vertex getNearestVertex(Vertex v1, Vertex v2, Segment segment) {
	if (segment.start.getDistance(v1) + segment.end.getDistance(v1)
			> segment.start.getDistance(v2) + segment.end.getDistance(v2))
		return v2;
	else 
		return v1;
}

/**
 * Returns the subpath for a split on the given path at the given segment.
 * @param path the path
 * @param segment the segment
 * @return the new subpath
 */
private Path getSubpathForSplit(Path path, Segment segment) {
	Path newPath = path.getSubPath(segment);
	paths.add(newPath);
	subPaths.add(newPath);
	return newPath;
}

/**
 * Grows all obstacles in in routing and tests for new intersections
 */
private void growObstacles() {
	growPassChangedObstacles = false;
	for (int i = 0; i < NUM_GROW_PASSES; i++) {
		if (i == 0 || growPassChangedObstacles)
			growObstaclesPass();
	}	
}
	
/**
 * Performs a single pass of the grow obstacles step, this can be repeated as desired.
 * Grows obstacles, then tests paths against the grown obstacles.
 */
private void growObstaclesPass() {
	// grow obstacles
	for (int i = 0; i < allObstacles.size(); i++)
		((Obstacle)allObstacles.get(i)).growVertices();
	
	// go through paths and test segments
	for (int i = 0; i < paths.size(); i++) {
		Path path = (Path) paths.get(i);

		for (int e = 0; e < path.excludedObstacles.size(); e++)
			((Obstacle)path.excludedObstacles.get(e)).exclude = true;
	
		if (path.grownSegments.size() == 0) {
			for (int s = 0; s < path.segments.size(); s++)
				testBentSegment((Segment)path.segments.get(s), -1, path);
		} else {
			int counter = 0;
			List currentSegments = new ArrayList(path.grownSegments);
			for (int s = 0; s < currentSegments.size(); s++)
				counter += testBentSegment((Segment)currentSegments.get(s), s + counter, path);
		}
		
		for (int e = 0; e < path.excludedObstacles.size(); e++)
			((Obstacle)path.excludedObstacles.get(e)).exclude = false;
		
	}
	
	// revert obstacles
	for (int i = 0; i < allObstacles.size(); i++)
		((Obstacle)allObstacles.get(i)).shrinkVertices();
}

/**
 * Adds an obstacle to the routing
 * @param obs the obstacle
 */
private void internalAddObstacle(Obstacle obs) {
	allObstacles.add(obs);
	testPaths(obs);
}

/**
 * Removes an obstacle from the routing.
 * @param rect the bounds of the obstacle
 * @return the obstacle removed
 */
private Obstacle internalRemoveObstacle(Rectangle rect) {
	Obstacle obs = null;
	int index = -1;
	for (int i = 0; i < allObstacles.size(); i++) {
		obs = (Obstacle)allObstacles.get(i);
		if (obs.equals(rect)) {
			index = i;
			break;
		}
	}
		
	allObstacles.remove(index);
	
	dirtyPathsOn(obs.bottomLeft);
	dirtyPathsOn(obs.topLeft);
	dirtyPathsOn(obs.bottomRight);
	dirtyPathsOn(obs.topRight);

	for (int p = 0; p < paths.size(); p++) {
		Path path = (Path)paths.get(p);
		if (path.isDirty)
			continue;
		if (path.isObstacleVisible(obs)) 
			path.isDirty = true;
	}
	
	return obs;
}

/**
 * Labels the given path's vertices as innies, or outies, as well as determining if this
 * path is inverted.
 * @param path the path
 */
private void labelPath(Path path) {
	Segment segment = null;
	Segment nextSegment = null;
	Vertex vertex = null;
	boolean agree = false;
	for (int v = 0; v < path.grownSegments.size() - 1; v++) {
		if (nextSegment != null)
			segment = nextSegment;
		else
			segment = (Segment) path.grownSegments.get(v);
		nextSegment = (Segment) path.grownSegments.get(v + 1);
		vertex = segment.end;
		long crossProduct = segment.crossProduct(new Segment(vertex, vertex.obs.center));
		
		if (vertex.type == Vertex.NOT_SET) {
			labelVertex(segment, crossProduct, path);
		} else if (!path.isInverted &&
				((crossProduct > 0 && vertex.type == Vertex.OUTIE)
				|| (crossProduct < 0 && vertex.type == Vertex.INNIE))) {
			if (agree) {
				// split detected.
				stack.push(getSubpathForSplit(path, segment));
				return;
			} else {
				path.isInverted = true;
				path.resetVertices(segment);
			}
		} else if (path.isInverted &&
				((crossProduct < 0 && vertex.type == Vertex.OUTIE)
				|| (crossProduct > 0 && vertex.type == Vertex.INNIE))) {
			// split detected.
			stack.push(getSubpathForSplit(path, segment));
			return;
		} else
			agree = true;
		
		if (vertex.paths != null) {
			for (int i = 0;i < vertex.paths.size();i++) {
				Path nextPath = (Path)vertex.paths.get(i);
				if (!nextPath.isMarked) {
					nextPath.isMarked = true;
					stack.push(nextPath);
				}
			}
		}
		
		vertex.addPath(path, segment, nextSegment);
	}
}

/**
 * Labels all path's vertices in the routing.
 */
private void labelPaths() {	
	Path path = null;
	for (int i = 0; i < paths.size(); i++) {
		path = (Path) paths.get(i);
		stack.push(path);
	}

	while (!stack.isEmpty()) {
		path = stack.pop();
		if (!path.isMarked) {
			path.isMarked = true;
			labelPath(path);
		}
	}
	
	// revert is marked so we can use it again in ordering.
	for (int i = 0;i < paths.size(); i++) {
		path = (Path)paths.get(i);
		path.isMarked = false;
	}
}

/**
 * Labels the vertex at the end of the semgent based on the cross product.
 * @param segment the segment to this vertex
 * @param crossProduct the cross product of this segment and a segment to the obstacles center
 * @param path the path
 */
private void labelVertex(Segment segment, long crossProduct, Path path) {
//	 assumes vertex in question is segment.end
	if (crossProduct > 0) {
		if (path.isInverted)
			segment.end.type = Vertex.OUTIE;
		else
			segment.end.type = Vertex.INNIE;
	} else if (crossProduct < 0) {
		if (path.isInverted) 
			segment.end.type = Vertex.INNIE;
		else
			segment.end.type = Vertex.OUTIE;
	} else if (segment.start.type != Vertex.NOT_SET)
		segment.end.type = segment.start.type;
	else
		segment.end.type = Vertex.INNIE;
}

/**
 * Orders the path by comparing its angle at shared vertices with other paths.
 * @param path the path
 */
private void orderPath(Path path) {
	if (path.isMarked)
		return;
	path.isMarked = true;
	Segment segment = null;
	Vertex vertex = null;
	for (int v = 0; v < path.grownSegments.size() - 1; v++) {
		segment = (Segment) path.grownSegments.get(v);
		vertex = segment.end;
		double thisAngle = ((Double)vertex.pathsToSegmentsMap.get(path)).doubleValue();
		if (path.isInverted)
			thisAngle = -thisAngle;
			
		for (int i = 0; i < vertex.paths.size(); i++) {
			Path vPath = (Path)vertex.paths.get(i);
			if (!vPath.isMarked) {
				double otherAngle = ((Double)vertex.pathsToSegmentsMap.get(vPath)).doubleValue();
				
				if (vPath.isInverted)
					otherAngle = -otherAngle;
					
				if (otherAngle < thisAngle)
					orderPath(vPath);
			}
		}
	}

	orderedPaths.add(path);
}

/**
 * Orders all paths in the graph.
 */
private void orderPaths() {
	orderedPaths.clear();
	
	for (int i = 0; i < paths.size(); i++) {
		Path path = (Path) paths.get(i);
		orderPath(path);
	}	
}

/**
 * Populates the parent paths with all the child paths that were created to represent
 * bendpoints.
 */
private void populateParentPaths() {
	// only populate those paths with children paths.
	Iterator keyItr = pathsToChildPaths.keySet().iterator();
	while (keyItr.hasNext()) {
		Path path = (Path)keyItr.next();
		
		// reset points.
		path.points.removeAllPoints();
		
		List childPaths = (List)pathsToChildPaths.get(path);
		Path childPath = null;
		
		for (int i = 0; i < childPaths.size(); i++) {
			childPath = (Path)childPaths.get(i);
			path.points.addAll(childPath.getPoints());
			// path will overlap
			path.points.removePoint(path.points.size() - 1);
		}
		
		// add last point.
		path.points.addPoint(childPath.points.getLastPoint());	
	}
}

/**
 * Reconnects all subpaths.
 */
private void reconnectSubpaths() {
	for (int p = 0; p < orderedPaths.size(); p++) {
		Path path = (Path)orderedPaths.get(p);
		path.reconnectSubPaths();
	}

	orderedPaths.removeAll(subPaths);
	paths.removeAll(subPaths);
	subPaths.clear();
}

/**
 * Removes the obstacle with the rectangle's bounds from the routing.
 * 
 * @param rect the bounds of the obstacle to remove
 */
public void removeObstacle(Rectangle rect) {
	internalRemoveObstacle(rect);
}

/**
 * Removes the given path from the routing.
 * 
 * @param path the path to remove.
 */
public void removePath(Path path) {
	allPaths.remove(path);
	if (pathsToChildPaths.containsKey(path)) {
		List childPaths = (List)pathsToChildPaths.get(path);
		for (int i = 0; i < childPaths.size(); i++) {
			paths.remove(childPaths.get(i));
		}
	} else 
		paths.remove(path);
}

/**
 * Resets exclude field on all obstacles
 */
private void resetObstacleExclusions() {
	for (int i = 0; i < allObstacles.size(); i++)
		((Obstacle)allObstacles.get(i)).exclude = false;
}

/**
 * Resets all vertices found on paths and obstacles.
 */
private void resetVertices() {
	for (int i = 0; i < allObstacles.size(); i++) {
		Obstacle obs = (Obstacle)allObstacles.get(i);
		obs.reset();
	}
	for (int i = 0; i < paths.size(); i++) {
		Path path = (Path)paths.get(i);
		path.start.fullReset();
		path.end.fullReset();
	}
}

/**
 * Updates the points in the paths in order to represent the current solution 
 * with the given paths and obstacles.
 *
 * @return returns the number of paths solved.
 */
public int solve() {
	updateChildPaths();
	
	int numSolved = solveDirtyPaths();
	
	countVertices();
	
	checkVertexIntersections();
	
	growObstacles();
	
	labelPaths();
	
	orderPaths();
	
	bendPaths();

	reconnectSubpaths();
	
	populateParentPaths();
	
	return numSolved;
}

/**
 * Solves paths that are dirty.
 * @return number of dirty paths
 */
private int solveDirtyPaths() {
	int numSolved = 0;
	boolean pathFoundCheck = false;
	
	for (int i = 0; i < paths.size(); i++) {
		Path path = (Path)paths.get(i);
		path.refreshExcludedObstacles(allObstacles);
		if (!path.isDirty) {
			path.reset();
			continue;
		}
		
		numSolved++;		
		path.fullReset();
		
		pathFoundCheck = path.generateShortestPath(allObstacles);
		if (!pathFoundCheck || path.end.cost > path.threshold) {
			// path not found, or path found was too long
			resetVertices();
			path.fullReset();
			path.threshold = 0;
			pathFoundCheck = path.generateShortestPath(allObstacles);
		}
		
		resetVertices();
	}
	
	resetObstacleExclusions();
	
	if (numSolved == 0)
		resetVertices();
	
	return numSolved;
}

/**
 * Tests a segment that has been offset for new intersections 
 * @param segment the segment
 * @param index the index of the segment along the path
 * @param path the path
 * @return 1 if new segments have been inserted
 */
private int testBentSegment(Segment segment, int index, Path path) {
	for (int i = 0; i < allObstacles.size(); i++) {
		Obstacle obs = (Obstacle) allObstacles.get(i);
		
		if (segment.end.obs == obs || segment.start.obs == obs || obs.exclude)
			continue;
		Vertex vertex = null;

		if (segment.getSlope() < 0) {
			if (segment.intersects(obs.topLeft.x - Vertex.BEND_OFFSET, obs.topLeft.y - Vertex.BEND_OFFSET, 
				obs.bottomRight.x + Vertex.BEND_OFFSET, obs.bottomRight.y + Vertex.BEND_OFFSET))
				vertex = getNearestVertex(obs.topLeft, obs.bottomRight, segment);
			else if (segment.intersects(obs.bottomLeft.x - Vertex.BEND_OFFSET, obs.bottomLeft.y + Vertex.BEND_OFFSET, 
				obs.topRight.x + Vertex.BEND_OFFSET, obs.topRight.y - Vertex.BEND_OFFSET))
				vertex = getNearestVertex(obs.bottomLeft, obs.topRight, segment);
		} else {
			if (segment.intersects(obs.bottomLeft.x - Vertex.BEND_OFFSET, obs.bottomLeft.y + Vertex.BEND_OFFSET, 
				obs.topRight.x + Vertex.BEND_OFFSET, obs.topRight.y - Vertex.BEND_OFFSET))
				vertex = getNearestVertex(obs.bottomLeft, obs.topRight, segment);
			else if (segment.intersects(obs.topLeft.x - Vertex.BEND_OFFSET, obs.topLeft.y - Vertex.BEND_OFFSET, 
				obs.bottomRight.x + Vertex.BEND_OFFSET, obs.bottomRight.y + Vertex.BEND_OFFSET))
				vertex = getNearestVertex(obs.topLeft, obs.bottomRight, segment);
		}

		if (vertex != null) {
			Rectangle vRect = vertex.getDeformedRectangle(Vertex.BEND_OFFSET);
			if (segment.end.obs != null) {
				Rectangle endRect = segment.end.getDeformedRectangle(Vertex.BEND_OFFSET);
				if (vRect.intersects(endRect))
					continue;
			}
			if (segment.start.obs != null) {
				Rectangle startRect = segment.start.getDeformedRectangle(Vertex.BEND_OFFSET);
				if (vRect.intersects(startRect))
					continue;
			}
			
			Segment newSegmentStart = new Segment(segment.start, vertex);
			Segment newSegmentEnd = new Segment(vertex, segment.end);
			
			vertex.totalCount++;
			vertex.shortestDistanceChecked = false;
			
			vertex.shrink();
			checkVertexForIntersections(vertex);
			vertex.grow();
			
			if (vertex.shortestDistance != 0)
				vertex.updateOffset();
		
			growPassChangedObstacles = true;
			
			if (index != -1) {
				path.grownSegments.remove(segment);
				path.grownSegments.add(index, newSegmentStart);
				path.grownSegments.add(index + 1, newSegmentEnd);
			} else {
				path.grownSegments.add(newSegmentStart);
				path.grownSegments.add(newSegmentEnd);
			}
			return 1;
		}
	}
	if (index == -1) 
		path.grownSegments.add(segment);
	return 0;
}

/**
 * Tests a path to see if it contains the given obstacle and should be dirty.
 * @param path the path
 * @param obs the obstacle
 */
private void testPath(Path path, Obstacle obs) {
	for (int s = 0; s < path.segments.size(); s++) {
		Segment segment = (Segment)path.segments.get(s);
		if (testSegment(segment, obs, path)) {
			path.isDirty = true;
			return;
		}
	}
}

/**
 * Tests all paths against the given obstacle
 * @param obs the obstacle
 */
private void testPaths(Obstacle obs) {
	for (int i = 0; i < paths.size(); i++) {
		Path path = (Path)paths.get(i);
		testPath(path, obs);
	}
}

/**
 * Tests the segment to see if it intersects the given obstacle
 * 
 * @param segment the segment
 * @param obs the obstacle
 * @param path the path
 * @return true if the segment intersects the obstacle
 */
private boolean testSegment(Segment segment, Obstacle obs, Path path) {
	if (segment.end.obs == obs || segment.start.obs == obs)
		return false;
	if (path.excludedObstacles.contains(obs))
		return false;
	
	if (segment.intersects(obs.topLeft, obs.bottomRight) 
			|| segment.intersects(obs.bottomLeft, obs.topRight)
			|| obs.contains(segment.start) || obs.contains(segment.end))
		return true;
	
	return false;	
}

/**
 * Resyncs the parent paths with any new child paths that are necessary because bendpoints
 * have been added to the parent path.
 */
private void updateChildPaths() {
	for (int i = 0; i < allPaths.size(); i++) {
		Path path = (Path)allPaths.get(i);
		if (path.isDirty) {
			// ditch old paths, even if they dont exist
			List childPaths = (List)pathsToChildPaths.remove(path);
			if (childPaths != null)
				paths.removeAll(childPaths);
			
			// generate new paths if necessary
			List bendPoints = path.bendPoints;
			if (bendPoints != null && !bendPoints.isEmpty()) {
				// make sure path is not in working paths.
				paths.remove(path);
				
				List newPaths = new ArrayList(bendPoints.size() + 1);
				Path newPath = null;
				Vertex prevVertex = path.start;
				Vertex currVertex = null;
				
				for (int b = 0; b < bendPoints.size(); b++) {
					Bendpoint bp = (Bendpoint)bendPoints.get(b);
					currVertex = new Vertex(bp.getLocation(), null);
					newPath = new Path(prevVertex, currVertex);
					newPaths.add(newPath);
					paths.add(newPath);
					prevVertex = currVertex;
				}
				
				newPath = new Path(prevVertex, path.end);
				newPaths.add(newPath);
				paths.add(newPath);
				
				pathsToChildPaths.put(path, newPaths);
			} else {
				if (childPaths != null) {
					// path no longer has child paths, but used to.
					pathsToChildPaths.remove(path);
					paths.add(path);
				}
			}
		}
	}
}

/**
 * Updates the position of an existing obstacle. 
 * @param oldBounds the old bounds(used to find the obstacle)
 * @param newBounds the new bounds
 */
public void updateObstacle(Rectangle oldBounds, Rectangle newBounds) {
	Obstacle obs = internalRemoveObstacle(oldBounds);
	
	obs.init(newBounds);
	
	internalAddObstacle(obs);
}

}