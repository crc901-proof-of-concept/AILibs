package jaicore.graphvisualizer.gui;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.graphstream.graph.Edge;
import org.graphstream.graph.Graph;
import org.graphstream.graph.Node;
import org.graphstream.graph.implementations.SingleGraph;
import org.graphstream.ui.fx_viewer.FxViewPanel;
import org.graphstream.ui.fx_viewer.FxViewer;
import org.graphstream.ui.fx_viewer.util.FxMouseManager;
import org.graphstream.ui.view.ViewerListener;
import org.graphstream.ui.view.ViewerPipe;
import org.graphstream.ui.view.util.InteractiveElement;

import com.google.common.eventbus.Subscribe;

import jaicore.graphvisualizer.events.controlEvents.EnableColouring;
import jaicore.graphvisualizer.events.graphEvents.GraphInitializedEvent;
import jaicore.graphvisualizer.events.graphEvents.NodeParentSwitchEvent;
import jaicore.graphvisualizer.events.graphEvents.NodeReachedEvent;
import jaicore.graphvisualizer.events.graphEvents.NodeRemovedEvent;
import jaicore.graphvisualizer.events.graphEvents.NodeTypeSwitchEvent;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
import javafx.scene.shape.Rectangle;

public class GraphVisualization<V, E> {

	protected Graph graph;
	protected FxViewer viewer;
	protected FxViewPanel viewPanel;

	protected int nodeCounter = 0;

	protected List<V> roots;

	protected final ConcurrentMap<V, Node> ext2intNodeMap = new ConcurrentHashMap<>();
	protected final ConcurrentMap<Node, V> int2extNodeMap = new ConcurrentHashMap<>();
	protected final ConcurrentMap<Node, Double> int2ValueMap = new ConcurrentHashMap<>();

	protected boolean loop = true;

	protected ViewerPipe pipe;
	Thread pipeThread;

	private final ObjectEvaluator<V> evaluator;
	private boolean evaluation;

	private double bestValue;
	private double worstValue;

	private final StackPane pane;
	private Rectangle gradient;

	private final Label minLabel;
	private final Label maxLabel;

	public GraphVisualization(final ObjectEvaluator<V> evaluator) {
		this.evaluator = evaluator;
		this.roots = new ArrayList<>();
		this.graph = new SingleGraph("Search-Graph");
		this.bestValue = Double.MAX_VALUE;
		this.worstValue = -1;
		this.pane = new StackPane();
		this.pane.setAlignment(Pos.TOP_RIGHT);

		if (this.evaluator == null) {
			this.graph.setAttribute("ui.stylesheet", "url('conf/searchgraph.css')");

		} else {
			this.graph.setAttribute("ui.stylesheet", "url('conf/heatmap.css')");
			this.evaluation = true;
			this.gradient = this.createColorGradient();

		}
		try {
			this.viewer = new FxViewer(this.graph, FxViewer.ThreadingModel.GRAPH_IN_ANOTHER_THREAD);
			this.viewer.enableAutoLayout();
		} catch (Exception e) {
			e.printStackTrace();
			System.exit(0);
		}

		this.viewPanel = (FxViewPanel) this.viewer.addDefaultView(false);

		this.pipe = this.viewer.newViewerPipe();

		this.viewer.getDefaultView().setMouseManager(new FxMouseManager(EnumSet.of(InteractiveElement.EDGE, InteractiveElement.NODE, InteractiveElement.SPRITE)));

		this.pipeThread = new Thread() {
			@Override
			public void run() {
				GraphVisualization.this.loopPump();
			}
		};
		this.pane.getChildren().add(this.viewPanel);
		this.pipeThread.start();

		this.maxLabel = new Label();
		this.minLabel = new Label();
		if (this.evaluation) {
			this.pane.getChildren().add(this.gradient);
			Platform.runLater(() -> {

				this.maxLabel.setTextFill(Color.CYAN);
				this.minLabel.setTextFill(Color.CYAN);
				this.pane.getChildren().add(this.maxLabel);

				this.minLabel.setTranslateY(485);
				this.pane.getChildren().add(this.minLabel);
			});
		}

	}

	public javafx.scene.Node getFXNode() {
		return this.pane;
	}

	@Subscribe
	public synchronized void receiveControlEvent(final EnableColouring event) {
		this.evaluation = event.isColouring();
		this.toggleColouring(this.evaluation);
	}

	private void toggleColouring(final boolean colouring) {
		if (colouring) {
			this.graph.clearAttributes();
			this.graph.setAttribute("ui.stylesheet", "url('conf/heatmap.css')");
			this.gradient = this.createColorGradient();
			this.pane.getChildren().add(this.gradient);
			Platform.runLater(() -> {
				this.maxLabel.setTextFill(Color.CYAN);
				this.minLabel.setTextFill(Color.CYAN);
				this.pane.getChildren().add(this.maxLabel);

				this.minLabel.setTranslateY(485);
				this.pane.getChildren().add(this.minLabel);
			});

			this.update();
		} else {
			this.graph.setAttribute("ui.stylesheet", "url('conf/searchgraph.css')");
			this.pane.getChildren().remove(this.gradient);
			this.pane.getChildren().remove(this.maxLabel);
			this.pane.getChildren().remove(this.minLabel);

		}
		this.update();

	}

	@Subscribe
	public synchronized void receiveGraphInitEvent(final GraphInitializedEvent<V> e) {
		try {
			this.roots.add(e.getRoot());
			if (this.roots == null) {
				throw new IllegalArgumentException("Root must not be NULL");
			}
			this.newNode(this.roots.get(this.roots.size() - 1));
			this.ext2intNodeMap.get(this.roots.get(this.roots.size() - 1)).setAttribute("ui.class", "root");
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}

	@Subscribe
	public synchronized void receiveNewNodeEvent(final NodeReachedEvent<V> e) {
		try {
			if (!this.ext2intNodeMap.containsKey(e.getNode())) {
				this.newNode(e.getNode());
			}
			this.newEdge(e.getParent(), e.getNode());
			this.ext2intNodeMap.get(e.getNode()).setAttribute("ui.class", e.getType());
		} catch (Exception ex) {

		}
	}

	@Subscribe
	public synchronized void receiveNodeTypeSwitchEvent(final NodeTypeSwitchEvent<V> e) {
		try {
			if (this.roots.contains(e.getNode())) {
				return;
			}
			if (!this.ext2intNodeMap.containsKey(e.getNode())) {
				throw new NoSuchElementException("Cannot switch type of node " + e.getNode() + ". This node has not been reached previously.");
			}
			this.ext2intNodeMap.get(e.getNode()).setAttribute("ui.class", e.getType());
		} catch (Exception ex) {

		}
	}

	@Subscribe
	public synchronized void receivedNodeParentSwitchEvent(final NodeParentSwitchEvent<V> e) {
		try {
			if (!this.ext2intNodeMap.containsKey(e.getNode()) && !this.ext2intNodeMap.containsKey(e.getNewParent())) {
				throw new NoSuchElementException("Cannot switch parent of node " + e.getNode() + ". Either the node or the new parent node has not been reached previously.");
			}

			this.removeEdge(e.getOldParent(), e.getNode());
			this.newEdge(e.getNewParent(), e.getNode());

		} catch (Exception ex) {

		}
	}

	@Subscribe
	public synchronized void receiveNodeRemovedEvent(final NodeRemovedEvent<V> e) {
		try {
			Node node = this.ext2intNodeMap.get(e.getNode());
			this.graph.removeNode(this.ext2intNodeMap.get(e.getNode()));
			this.ext2intNodeMap.remove(e.getNode());
			this.int2extNodeMap.remove(node);

			if (this.int2ValueMap.containsKey(node)) {
				double value = this.int2ValueMap.get(node);
				this.int2ValueMap.remove(node);
				if (value == this.bestValue) {
					if (!this.int2ValueMap.containsValue(value) && !this.int2ValueMap.isEmpty()) {
						double best = this.int2ValueMap.values().stream().min(Comparator.comparing(Double::valueOf)).get();
						this.bestValue = best;
						Platform.runLater(() -> {
							this.minLabel.setText(Double.toString(this.bestValue));
							this.update();
						});
					}
				}
				if (value == this.worstValue) {
					if (!this.int2ValueMap.containsValue(value) && !this.int2ValueMap.isEmpty()) {
						double worst = this.int2ValueMap.values().stream().max(Comparator.comparing(Double::valueOf)).get();
						this.worstValue = worst;
						Platform.runLater(() -> {
							this.maxLabel.setText(Double.toString(this.worstValue));
							this.update();
						});
					}
				}
			}

		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}

	/**
	 * Creation of a new node in the graph
	 *
	 * @param newNodeExt The external representation of the node
	 * @return The internal representation of the node
	 */
	protected synchronized Node newNode(final V newNodeExt) {

		/* create new node */
		final String nodeId = "n" + (this.nodeCounter++);
		if (this.ext2intNodeMap.containsKey(newNodeExt) || this.graph.getNode(nodeId) != null) {
			throw new IllegalArgumentException("Cannot insert node " + newNodeExt + " because it is already known.");
		}
		final Node newNodeInt = this.graph.addNode(nodeId);

		/*
		 * store relation between node in graph and internal representation of the node
		 */
		this.ext2intNodeMap.put(newNodeExt, newNodeInt);
		this.int2extNodeMap.put(newNodeInt, newNodeExt);

		/*
		 * compute fvalue if possible
		 */
		if (this.evaluator != null) {
			try {
				double value = this.evaluator.evaluate(newNodeExt);
				this.int2ValueMap.put(newNodeInt, value);
				if (value < this.bestValue) {
					this.bestValue = value;
					Platform.runLater(() -> {
						this.minLabel.setText(Double.toString(this.bestValue));
						this.update();
					});

				}
				if (value > this.worstValue) {
					this.worstValue = value;
					Platform.runLater(() -> {
						this.maxLabel.setText(Double.toString(this.worstValue));
						this.update();
					});

				}

				if (!this.roots.contains(newNodeExt)) {
					this.colourNode(newNodeInt, value);
				}

			} catch (Exception e) {

			}
		}

		/* store relation between node an parent in internal model */
		return newNodeInt;
	}

	/**
	 * Creation of a new edge in the graph
	 *
	 * @param from The source of the edge
	 * @param to   The endpoint of the edge
	 * @return The new edge
	 */
	protected synchronized Edge newEdge(final V from, final V to) {
		final Node fromInt = this.ext2intNodeMap.get(from);
		final Node toInt = this.ext2intNodeMap.get(to);
		if (fromInt == null) {
			throw new IllegalArgumentException("Cannot insert edge between " + from + " and " + to + " since node " + from + " does not exist.");
		}
		if (toInt == null) {
			throw new IllegalArgumentException("Cannot insert edge between " + from + " and " + to + " since node " + to + " does not exist.");
		}
		final String edgeId = fromInt.getId() + "-" + toInt.getId();
		return this.graph.addEdge(edgeId, fromInt, toInt, false);
	}

	protected synchronized boolean removeEdge(final V from, final V to) {
		// for(Edge e: graph.getEachEdge()) {
		// if (e.getSourceNode().equals(this.ext2intNodeMap.get(from)) && e.getTargetNode().equals(this.ext2intNodeMap.get(to))) {
		// graph.removeEdge(e);
		// return true;
		// }
		// }
		return false;
	}

	/**
	 * Resets the visualization of the graph
	 */
	public void reset() {
		this.ext2intNodeMap.clear();
		this.int2extNodeMap.clear();
		this.nodeCounter = 0;
		this.graph.clear();
		if (this.evaluator == null) {
			this.graph.setAttribute("ui.stylesheet", "url('conf/searchgraph.css')");
		} else {
			this.graph.setAttribute("ui.stylesheet", "url('conf/heatmap.css')");
			this.gradient = this.createColorGradient();
		}
	}

	/**
	 * Used to enable node pushing etc.
	 */
	private void loopPump() {
		while (this.loop) {
			try {
				Thread.sleep(100);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}

			this.pipe.pump();
		}
	}

	private V getNodeOfString(final String name) {
		return this.int2extNodeMap.get(this.graph.getNode(name));
	}

	public void addNodeListener(final NodeListener<V> listener) {
		this.pipe.addViewerListener(new ViewerListener() {
			@Override
			public void viewClosed(final String id) {

			}

			@Override
			public void buttonPushed(final String id) {
				listener.buttonPushed(GraphVisualization.this.getNodeOfString(id));
			}

			@Override
			public void buttonReleased(final String id) {
				listener.buttonReleased(GraphVisualization.this.getNodeOfString(id));

			}

			@Override
			public void mouseOver(final String id) {
				listener.mouseOver(GraphVisualization.this.getNodeOfString(id));

			}

			@Override
			public void mouseLeft(final String id) {
				listener.mouseLeft(GraphVisualization.this.getNodeOfString(id));
			}
		});
	}

	private void colourNode(final Node node, final double value) {
		float color = 1;
		float x = (float) (value - this.bestValue);
		float y = (float) (this.worstValue - this.bestValue);
		color = x / y;
		if (Float.isNaN(color)) {
			color = 1;
		}
		if (this.evaluation) {
			node.setAttribute("ui.color", color);
		}
	}

	public void update() {
		for (V n : this.ext2intNodeMap.keySet()) {

			double value = this.evaluator.evaluate(n);
			this.colourNode(this.ext2intNodeMap.get(n), value);
		}

	}

	/**
	 * Create the color gradient
	 * @return Creates the color gradient
	 */
	protected Rectangle createColorGradient() {

		Rectangle box = new Rectangle(50, 500);
		Stop[] stops = new Stop[] { new Stop(0, Color.BLUE), new Stop(1, Color.RED) };
		ArrayList<Stop> list = new ArrayList<Stop>();

		try {
			Files.lines(Paths.get("conf/heatmap.css")).filter(line -> line.contains("fill-color"))

					.filter(line -> !line.contains("/*")).forEach(line -> {
						String s = line.replace("fill-color: ", "").replace(";", "").replace(" ", "");
						String[] a = s.split(",");
						if (a.length > 1) {
							double d = 1.0 / (a.length - 1);
							for (int i = 0; i < a.length; i++) {
								list.add(new Stop(d * i, Color.web(a[i].trim())));
							}
						}

					});
		} catch (IOException e) {
			e.printStackTrace();
		}
		stops = list.toArray(new Stop[0]);
		LinearGradient lg = new LinearGradient(0, 1, 0, 0, true, CycleMethod.NO_CYCLE, stops);
		box.setFill(lg);
		this.gradient = box;
		return box;
	}
}