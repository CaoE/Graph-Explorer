/*
 * Copyright 2011-2013 Vaadin Ltd.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not 
 * use this file except in compliance with the License. You may obtain a copy of 
 * the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0.html
 * 
 * Unless required by applicable law or agreed to in writing, software 
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT 
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the 
 * License for the specific language governing permissions and limitations under 
 * the License. 
 */
package com.vaadin.graph.client;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import com.google.gwt.animation.client.Animation;
import com.google.gwt.dom.client.Style;
import com.google.gwt.dom.client.Style.Unit;
import com.google.gwt.event.dom.client.MouseDownEvent;
import com.google.gwt.event.dom.client.MouseDownHandler;
import com.google.gwt.event.dom.client.MouseMoveEvent;
import com.google.gwt.event.dom.client.MouseMoveHandler;
import com.google.gwt.event.dom.client.MouseUpEvent;
import com.google.gwt.event.dom.client.MouseUpHandler;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.Element;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.ui.HTML;
import com.vaadin.graph.shared.NodeProxy;

/**
 * Presenter/controller for a node in a graph.
 * 
 * @author Marlon Richert @ <a href="http://vaadin.com/">Vaadin</a>
 */
class NodePresenter implements Controller, MouseDownHandler, MouseMoveHandler,
		MouseUpHandler {

	/** Set the CSS class name to allow styling. */
	public static final String CSS_CLASSNAME = "node";

	private final GraphExplorerConnector connector;
	private final NodeProxy model;
	private final Set<String> inArcSets = new HashSet<String>();
	private final Set<String> outArcSets = new HashSet<String>();

	private final HTML view = new HTML();
	private final NodeAnimation animation = new NodeAnimation();

	private int dragStartX;
	private int dragStartY;
	private boolean mouseDown;
	private boolean dragging;

	NodePresenter(GraphExplorerConnector connector, NodeProxy model) {
		this.connector = connector;
		this.model = model;

		view.setTitle(model.getId());
		Style style = view.getElement().getStyle();
		style.setLeft(model.getX(), Unit.PX);
		style.setTop(model.getY(), Unit.PX);

		view.addDomHandler(this, MouseDownEvent.getType());
		view.addDomHandler(this, MouseMoveEvent.getType());
		view.addDomHandler(this, MouseUpEvent.getType());

		connector.getWidget().add(view);
	}

	public void onMouseDown(MouseDownEvent event) {
		setMouseDown(true);
		updateCSS();
		DOM.setCapture(view.getElement());
		dragStartX = event.getX();
		dragStartY = event.getY();
		event.preventDefault();
	}

	public void onMouseMove(MouseMoveEvent event) {
		if (isMouseDown()) {
			setDragging(true);
			updateCSS();
			model.setX(event.getX() + model.getX() - dragStartX);
			model.setY(event.getY() + model.getY() - dragStartY);
			onUpdateInModel();
			int clientX = event.getClientX();
			int clientY = event.getClientY();
			boolean outsideWindow = clientX < 0 || clientY < 0
					|| clientX > Window.getClientWidth()
					|| clientY > Window.getClientHeight();
			if (outsideWindow) {
				connector.updateNode(model);
				setDragging(false);
			}
		}
		event.preventDefault();
	}

	public void onMouseUp(MouseUpEvent event) {
		Element element = view.getElement();
		if (!isDragging()) {
			updateCSS();
			limitToBoundingBox();
			if (NodeProxy.EXPANDED.equals(model.getState())) {
				model.setState(NodeProxy.COLLAPSED);
				for (NodePresenter neighbor : getNeighbors()) {
					boolean collapsed = NodeProxy.COLLAPSED.equals(neighbor.getModel().getState());
					boolean leafNode = neighbor.degree() == 1;
					if (collapsed && leafNode) {
						connector.getGraph().removeNode(neighbor.getModel().getId());
					}
				}
			}
			connector.toggle(model);
		} else {
			connector.updateNode(model);
			setDragging(false);
		}
		setMouseDown(false);
		DOM.releaseCapture(element);
		event.preventDefault();
	}

	public void onRemoveFromModel() {
		for (String each : inArcSets) {
			connector.getGraph().removeArc(each);
		}
		for (String each : outArcSets) {
			connector.getGraph().removeArc(each);
		}
		view.removeFromParent();
	}

	private void limitToBoundingBox() {
		Element element = view.getElement();
		Style style = element.getStyle();

		int width = element.getOffsetWidth();
		model.setWidth(width);
		int xRadius = width / 2;
		int leftEdge = model.getX() - xRadius;
		leftEdge = limit(0, leftEdge, connector.getWidget().getOffsetWidth() - width);
		model.setX(leftEdge + xRadius);
		style.setLeft(leftEdge, Unit.PX);

		int height = element.getOffsetHeight();
		model.setHeight(height);
		int yRadius = height / 2;
		int topEdge = model.getY() - yRadius;
		topEdge = limit(0, topEdge, connector.getWidget().getOffsetHeight() - height);
		model.setY(topEdge + yRadius);
		style.setTop(topEdge, Unit.PX);
	}

	public void onUpdateInModel() {
		StringBuilder html = new StringBuilder();
		if (model.getIconUrl() != null && !model.getIconUrl().isEmpty()) {
			html.append("<div class='icon'>");
			html.append("<img src='")
					.append(connector.getConnection().translateVaadinUri(
							model.getIconUrl())).append("'></img>");
			html.append("<div class='label'>").append(model.getContent())
					.append("</div>");
			html.append("</div>");
		} else {
			html.append("<div class='label'>").append(model.getContent())
					.append("</div>");
		}
		view.setHTML(html.toString());
		limitToBoundingBox();
		updateCSS();
		updateArcs();
	}

	NodeProxy getModel() {
		return model;
	}

	void addInArc(String arc) {
		inArcSets.add(arc);
	}

	void addOutArc(String arc) {
		outArcSets.add(arc);
	}

	private void updateCSS() {
		Element element = view.getElement();
		element.setClassName(CSS_CLASSNAME);
		element.addClassName(model.getState());
		element.addClassName(model.getKind());
		if (isMouseDown()) {
			element.addClassName("down");
		}
	}

	private void updateArcs() {
		for (String each : inArcSets) {
			ArcPresenter arc = connector.getGraph().getArc(each);
			if (arc != null) {
				arc.onUpdateInModel();
			}
		}
		for (String each : outArcSets) {
			ArcPresenter arc = connector.getGraph().getArc(each);
			if (arc != null) {
				arc.onUpdateInModel();
			}
		}
	}

	private Collection<NodePresenter> getNeighbors() {
		Set<NodePresenter> neighbors = new HashSet<NodePresenter>();
		for (String each : inArcSets) {
			ArcPresenter arc = connector.getGraph().getArc(each);
			if (arc != null) {
				NodePresenter node = arc.getFromNode();
				if (node != null) {
					neighbors.add(node);
				}
			}
		}
		for (String each : outArcSets) {
			ArcPresenter arc = connector.getGraph().getArc(each);
			if (arc != null) {
				NodePresenter node = arc.getToNode();
				if (node != null) {
					neighbors.add(node);
				}
			}
		}
		return neighbors;
	}

	private int degree() {
		int degree = 0;
		degree += inArcSets.size();
		degree += outArcSets.size();
		return degree;
	}

	void removeArc(String arc) {
		inArcSets.remove(arc);
		outArcSets.remove(arc);
	}

	/** Limits value to [min, max], so that min <= value <= max. */
	private static int limit(int min, int value, int max) {
		return Math.min(Math.max(min, value), max);
	}

	void move(int x, int y) {
		animation.targetX = x;
		animation.targetY = y;
		animation.run(500);
	}

	private boolean isDragging() {
		return dragging;
	}

	private void setDragging(boolean dragging) {
		this.dragging = dragging;
	}

	private boolean isMouseDown() {
		return mouseDown;
	}

	private void setMouseDown(boolean mouseDown) {
		this.mouseDown = mouseDown;
	}

	private class NodeAnimation extends Animation {
		private int targetX = 0;
		private int targetY = 0;

		@Override
		protected void onUpdate(double progress) {
			if (progress > 1) {
				progress = 1;
			}
			getModel().setX(
					(int) Math.round(progress * targetX + (1 - progress)
							* getModel().getX()));
			getModel().setY(
					(int) Math.round(progress * targetY + (1 - progress)
							* getModel().getY()));
			onUpdateInModel();
		}

		@Override
		protected void onCancel() {
			// do nothing
		}
	}
}
