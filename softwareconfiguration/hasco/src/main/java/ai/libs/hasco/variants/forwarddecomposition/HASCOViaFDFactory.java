package ai.libs.hasco.variants.forwarddecomposition;

import ai.libs.hasco.core.HASCOFactory;
import ai.libs.jaicore.planning.hierarchical.algorithms.forwarddecomposition.SimpleForwardDecompositionReducer;
import ai.libs.jaicore.planning.hierarchical.algorithms.forwarddecomposition.graphgenerators.tfd.TFDNode;
import ai.libs.jaicore.search.core.interfaces.IOptimalPathInORGraphSearchFactory;
import ai.libs.jaicore.search.probleminputs.GraphSearchWithPathEvaluationsInput;

public class HASCOViaFDFactory<S extends GraphSearchWithPathEvaluationsInput<TFDNode, String, V>, V extends Comparable<V>> extends HASCOFactory<S, TFDNode, String, V> {

	public HASCOViaFDFactory() {
		super();
		this.setPlanningGraphGeneratorDeriver(new SimpleForwardDecompositionReducer());
	}

	public HASCOViaFDFactory(final IOptimalPathInORGraphSearchFactory<S, TFDNode, String, V> searchFactory) {
		this();
		this.setSearchFactory(searchFactory);
	}
}
