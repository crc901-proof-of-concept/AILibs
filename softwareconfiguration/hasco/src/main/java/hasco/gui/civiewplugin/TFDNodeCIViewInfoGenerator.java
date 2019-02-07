package hasco.gui.civiewplugin;

import java.util.Collection;

import hasco.core.Util;
import hasco.model.Component;
import hasco.model.ComponentInstance;
import jaicore.graphvisualizer.plugin.nodeinfo.NodeInfoGenerator;
import jaicore.planning.hierarchical.algorithms.forwarddecomposition.graphgenerators.tfd.TFDNode;

public class TFDNodeCIViewInfoGenerator implements NodeInfoGenerator<TFDNode> {

	private Collection<Component> components;

	public TFDNodeCIViewInfoGenerator(final Collection<Component> components) {
		this.components = components;
	}

	@Override
	public String generateInfoForNode(final TFDNode node) {
		ComponentInstance ci = Util.getSolutionCompositionFromState(this.components, node.getState(), false);

		StringBuilder sb = new StringBuilder();

		if (ci != null) {
			sb.append(ci.toString());

		} else {
			sb.append("<i>No components chosen, yet.</i>");
		}

		return sb.toString();
	}
}
