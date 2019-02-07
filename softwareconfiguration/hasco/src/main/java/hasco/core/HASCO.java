package hasco.core;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import org.aeonbits.owner.ConfigFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import hasco.events.HASCOSolutionEvent;
import hasco.model.Component;
import hasco.model.ComponentInstance;
import hasco.model.Parameter;
import hasco.model.ParameterRefinementConfiguration;
import hasco.optimizingfactory.SoftwareConfigurationAlgorithm;
import hasco.reduction.HASCOReduction;
import jaicore.basic.ILoggingCustomizable;
import jaicore.basic.algorithm.AlgorithmExecutionCanceledException;
import jaicore.basic.algorithm.AlgorithmProblemTransformer;
import jaicore.basic.algorithm.events.AlgorithmEvent;
import jaicore.basic.algorithm.events.AlgorithmFinishedEvent;
import jaicore.basic.algorithm.events.AlgorithmInitializedEvent;
import jaicore.basic.algorithm.exceptions.AlgorithmException;
import jaicore.basic.algorithm.exceptions.DelayedCancellationCheckException;
import jaicore.basic.algorithm.exceptions.DelayedTimeoutCheckException;
import jaicore.basic.algorithm.exceptions.ObjectEvaluationFailedException;
import jaicore.graphvisualizer.plugin.graphview.GraphViewPlugin;
import jaicore.graphvisualizer.plugin.nodeinfo.NodeInfoGUIPlugin;
import jaicore.graphvisualizer.window.GraphVisualizationWindow;
import jaicore.logging.ToJSONStringUtil;
import jaicore.planning.core.EvaluatedSearchGraphBasedPlan;
import jaicore.planning.core.Plan;
import jaicore.planning.hierarchical.algorithms.forwarddecomposition.graphgenerators.tfd.TFDNodeInfoGenerator;
import jaicore.planning.hierarchical.problems.ceocipstn.CEOCIPSTNPlanningProblem;
import jaicore.planning.hierarchical.problems.htn.CostSensitiveHTNPlanningProblem;
import jaicore.planning.hierarchical.problems.htn.CostSensitivePlanningToSearchProblemTransformer;
import jaicore.search.algorithms.standard.bestfirst.events.EvaluatedSearchSolutionCandidateFoundEvent;
import jaicore.search.core.interfaces.GraphGenerator;
import jaicore.search.core.interfaces.IOptimalPathInORGraphSearch;
import jaicore.search.core.interfaces.IOptimalPathInORGraphSearchFactory;
import jaicore.search.model.other.EvaluatedSearchGraphPath;
import jaicore.search.model.travesaltree.JaicoreNodeInfoGenerator;
import jaicore.search.probleminputs.GraphSearchInput;
import jaicore.search.probleminputs.GraphSearchWithPathEvaluationsInput;
import javafx.application.Platform;
import javafx.embed.swing.JFXPanel;

/**
 * Hierarchically create an object of type T
 *
 * @author fmohr, wever
 *
 * @param <ISearch>
 * @param <N>
 * @param <A>
 * @param <V>
 */
public class HASCO<ISearch extends GraphSearchInput<N, A>, N, A, V extends Comparable<V>>
		extends SoftwareConfigurationAlgorithm<RefinementConfiguredSoftwareConfigurationProblem<V>, HASCOSolutionCandidate<V>, V> {

	private Logger logger = LoggerFactory.getLogger(HASCO.class);
	private String loggerName;

	/* problem and algorithm setup */
	private final IHASCOPlanningGraphGeneratorDeriver<N, A> planningGraphGeneratorDeriver;
	private final AlgorithmProblemTransformer<GraphSearchWithPathEvaluationsInput<N, A, V>, ISearch> searchProblemTransformer;
	private final IOptimalPathInORGraphSearchFactory<ISearch, N, A, V> searchFactory;

	/* working constants of the algorithms */
	private final CostSensitiveHTNPlanningProblem<CEOCIPSTNPlanningProblem, V> planningProblem;
	private final GraphSearchWithPathEvaluationsInput<N, A, V> searchProblem;
	private final IOptimalPathInORGraphSearch<ISearch, N, A, V> search;
	private final List<HASCOSolutionCandidate<V>> listOfAllRecognizedSolutions = new ArrayList<>();

	/* runtime variables of algorithm */
	private final TimeRecordingEvaluationWrapper<V> timeGrabbingEvaluationWrapper;

	/** Extension: Allow other clients to listen to search events */
	private final Set<Object> searchListeners = new HashSet<>();

	public HASCO(final RefinementConfiguredSoftwareConfigurationProblem<V> configurationProblem, final IHASCOPlanningGraphGeneratorDeriver<N, A> planningGraphGeneratorDeriver,
			final IOptimalPathInORGraphSearchFactory<ISearch, N, A, V> searchFactory,
			final AlgorithmProblemTransformer<GraphSearchWithPathEvaluationsInput<N, A, V>, ISearch> searchProblemTransformer) {
		super(ConfigFactory.create(HASCOConfig.class), configurationProblem);
		if (configurationProblem == null) {
			throw new IllegalArgumentException("Cannot work with configuration problem NULL");
		}
		this.planningGraphGeneratorDeriver = planningGraphGeneratorDeriver;
		this.searchFactory = searchFactory;
		this.searchProblemTransformer = searchProblemTransformer;
		this.timeGrabbingEvaluationWrapper = new TimeRecordingEvaluationWrapper<>(configurationProblem.getCompositionEvaluator());

		/* check whether there is a refinement config for each numeric parameter */
		Map<Component, Map<Parameter, ParameterRefinementConfiguration>> paramRefinementConfig = this.getInput().getParamRefinementConfig();
		for (Component c : this.getInput().getComponents()) {
			for (Parameter p : c.getParameters()) {
				if (p.isNumeric() && (!paramRefinementConfig.containsKey(c) || !paramRefinementConfig.get(c).containsKey(p))) {
					throw new IllegalArgumentException("No refinement config was delivered for numeric parameter " + p.getName() + " of component " + c.getName());
				}
			}
		}

		/* derive planning problem and search problem */
		this.logger.debug("Deriving search problem");
		RefinementConfiguredSoftwareConfigurationProblem<V> refConfigSoftwareConfigurationProblem = new RefinementConfiguredSoftwareConfigurationProblem<>(
				new SoftwareConfigurationProblem<V>(this.getInput().getComponents(), this.getInput().getRequiredInterface(), this.timeGrabbingEvaluationWrapper),
				this.getInput().getParamRefinementConfig());
		this.planningProblem = new HASCOReduction<V>().transform(refConfigSoftwareConfigurationProblem);
		if (this.logger.isDebugEnabled()) {
			String operations = this.planningProblem.getCorePlanningProblem().getDomain().getOperations().stream().map(
					o -> "\n\t\t" + o.getName() + "(" + o.getParams() + ")\n\t\t\tPre: " + o.getPrecondition() + "\n\t\t\tAdd List: " + o.getAddLists() + "\n\t\t\tDelete List: " + o.getDeleteLists())
					.collect(Collectors.joining());
			String methods = this.planningProblem
					.getCorePlanningProblem().getDomain().getMethods().stream().map(m -> "\n\t\t" + m.getName() + "(" + m.getParameters() + ") for task " + m.getTask() + "\n\t\t\tPre: "
							+ m.getPrecondition() + "\n\t\t\tPre Eval: " + m.getEvaluablePrecondition() + "\n\t\t\tNetwork: " + m.getNetwork().getLineBasedStringRepresentation())
					.collect(Collectors.joining());
			this.logger.debug("Derived the following HTN planning problem:\n\tOperations:{}\n\tMethods:{}", operations, methods);
		}
		this.searchProblem = new CostSensitivePlanningToSearchProblemTransformer<CEOCIPSTNPlanningProblem, V, N, A>(this.planningGraphGeneratorDeriver).transform(this.planningProblem);
		
		/* create search object */
		this.logger.debug("Creating and initializing the search object");
		this.searchFactory.setProblemInput(this.searchProblem, this.searchProblemTransformer);
		this.search = this.searchFactory.getAlgorithm();
	}

	@Override
	public AlgorithmEvent nextWithException() throws InterruptedException, AlgorithmExecutionCanceledException, TimeoutException, AlgorithmException {

		/* check on termination */
		logger.trace("Conducting next step in {}.", this.getId());
		try {
			this.checkAndConductTermination();
		} catch (DelayedTimeoutCheckException e1) {
			e1.printStackTrace();
			throw e1.getException();
		} catch (DelayedCancellationCheckException e1) {
			e1.printStackTrace();
			throw e1.getException();
		}
		logger.trace("No stop criteria have caused HASCO to stop up to now. Proceeding ...");

		/* act depending on state */
		switch (this.getState()) {
		case created: {
			this.logger.info("Starting HASCO run.");
			AlgorithmInitializedEvent event = this.activate();

			/* setup search algorithm */
			this.search.setNumCPUs(this.getNumCPUs());
			this.search.setTimeout(this.getTimeout());
			if (this.loggerName != null && this.loggerName.length() > 0 && this.search instanceof ILoggingCustomizable) {
				this.logger.info("Setting logger name of {} to {}", this.search, this.loggerName + ".search");
				((ILoggingCustomizable) this.search).setLoggerName(this.loggerName + ".search");
			} else {
				this.logger.info("Not setting the logger name of the search. Logger name of HASCO is {}. Search loggingCustomizable: {}", this.loggerName,
						(this.search instanceof ILoggingCustomizable));
			}
			if (this.getConfig().visualizationEnabled()) {
				this.logger.info("Launching graph visualization");
				new JFXPanel();
				Platform.runLater(new GraphVisualizationWindow(this.search, new GraphViewPlugin(), new NodeInfoGUIPlugin<>(new JaicoreNodeInfoGenerator<>(new TFDNodeInfoGenerator()))));

			}

			/* register external listeners */
			for (Object listener : this.searchListeners) {
				this.search.registerListener(listener);
			}

			/* now initialize the search */
			this.logger.debug("Initializing the search");
			boolean searchInitializationObserved = false;
			AlgorithmEvent searchEvent;
			do {
				searchEvent = this.search.nextWithException();
				logger.debug("Observing search event {}", searchEvent);
				searchInitializationObserved = (searchEvent instanceof AlgorithmInitializedEvent);
			} while (this.search.hasNext() && !searchInitializationObserved);
			if (!searchInitializationObserved) {
				throw new IllegalStateException("The search underlying HASCO could not be initialized successully.");
			}
			logger.debug("Search has been initialized.");
			logger.info("HASCO initialization completed.");
			return event;
		}
		case active: {

			/* step search */
			AlgorithmEvent searchEvent = this.search.nextWithException();
			if (searchEvent instanceof AlgorithmFinishedEvent) {
				logger.info("The search algorithm has finished. Terminating HASCO.");
				return this.terminate();
			}

			/* otherwise, if a solution has been found, we announce this finding to our listeners and memorize if it is a new best candidate */
			else if (searchEvent instanceof EvaluatedSearchSolutionCandidateFoundEvent) {
				this.logger.info("Received new solution from search, communicating this solution to the HASCO listeners.");
				@SuppressWarnings("unchecked")
				EvaluatedSearchSolutionCandidateFoundEvent<N, A, V> solutionEvent = (EvaluatedSearchSolutionCandidateFoundEvent<N, A, V>) searchEvent;
				EvaluatedSearchGraphPath<N, A, V> searchPath = solutionEvent.getSolutionCandidate();
				Plan plan = this.planningGraphGeneratorDeriver.getPlan(searchPath.getNodes());
				ComponentInstance objectInstance = Util.getSolutionCompositionForPlan(this.getInput().getComponents(), this.planningProblem.getCorePlanningProblem().getInit(), plan, true);
				V score;
				try {
					boolean scoreInCache = this.timeGrabbingEvaluationWrapper.hasEvaluationForComponentInstance(objectInstance);
//					assert (scoreInCache || solutionEvent.getSolutionCandidate().getScore() == null) : "Solution candidate has a score but is not in cache of the timeGrabbingEvaluationWrapper";
					if (scoreInCache) {
						score = solutionEvent.getSolutionCandidate().getScore();
					} else {
						score = this.timeGrabbingEvaluationWrapper.evaluate(objectInstance);
					}
				} catch (ObjectEvaluationFailedException e) {
					throw new AlgorithmException(e, "Could not evaluate component instance.");
				}
				EvaluatedSearchGraphBasedPlan<V, N> evaluatedPlan = new EvaluatedSearchGraphBasedPlan<>(plan, score, searchPath);
				HASCOSolutionCandidate<V> solution = new HASCOSolutionCandidate<>(objectInstance, evaluatedPlan,
						this.timeGrabbingEvaluationWrapper.getEvaluationTimeForComponentInstance(objectInstance));
				this.updateBestSeenSolution(solution);
				this.listOfAllRecognizedSolutions.add(solution);
				HASCOSolutionEvent<V> hascoSolutionEvent = new HASCOSolutionEvent<>(solution);
				this.post(hascoSolutionEvent);
				return hascoSolutionEvent;
			}

			/* if this is an ordinary graph search event, just return it */
			else
				return searchEvent;
		}
		default:
			throw new IllegalStateException("HASCO cannot do anything in state " + this.getState());
		}
	}
	
	public void registerListenerForSearch(final Object listener) {
		this.searchListeners.add(listener);
	}

	public void removeListenerForSearch(final Object listener) {
		this.searchListeners.remove(listener);
	}

	public GraphGenerator<N, A> getGraphGenerator() {
		return this.searchProblem.getGraphGenerator();
	}

	public CostSensitiveHTNPlanningProblem<CEOCIPSTNPlanningProblem, V> getPlanningProblem() {
		return this.planningProblem;
	}

	@Override
	public void cancel() {
		if (isCanceled()) {
			logger.debug("Ignoring cancel, because cancel has been triggered in the past already.");
			return;
		}
		logger.info("Received cancel, first processing the cancel locally, then forwarding to search.");
		super.cancel();
		if (this.search != null) {
			logger.info("Trigger cancel on search.");
			this.search.cancel();
		}
		logger.info("Finished, now terminating");
		this.terminate();
	}

	public IHASCOPlanningGraphGeneratorDeriver<N, A> getPlanningGraphGeneratorDeriver() {
		return this.planningGraphGeneratorDeriver;
	}

	public AlgorithmProblemTransformer<GraphSearchWithPathEvaluationsInput<N, A, V>, ISearch> getSearchProblemTransformer() {
		return this.searchProblemTransformer;
	}

	public HASCORunReport<V> getReport() {
		return new HASCORunReport<>(this.listOfAllRecognizedSolutions);
	}

	@Override
	protected void shutdown() {
		if (isShutdownInitialized()) {
			this.logger.debug("Shutdown has already been initialized, ignoring new shutdown request.");
			return;
		}
		this.logger.info("Entering HASCO shutdown routine.");
		super.shutdown();
		this.logger.debug("Cancelling search.");
		this.search.cancel();
		this.logger.debug("Shutdown of HASCO completed.");
	}

	@Override
	public HASCOConfig getConfig() {
		return (HASCOConfig) super.getConfig();
	}

	public boolean getVisualization() {
		return this.getConfig().visualizationEnabled();
	}

	public void setVisualization(final boolean visualization) {
		this.getConfig().setProperty(HASCOConfig.K_VISUALIZE, String.valueOf(visualization));
	}

	public IOptimalPathInORGraphSearchFactory<ISearch, N, A, V> getSearchFactory() {
		return searchFactory;
	}

	@Override
	public String getLoggerName() {
		return this.loggerName;
	}

	@Override
	public void setLoggerName(final String name) {
		this.logger.info("Switching logger from {} to {}", this.logger.getName(), name);
		this.loggerName = name;
		this.logger = LoggerFactory.getLogger(name);
		this.logger.info("Activated logger {} with name {}", name, this.logger.getName());
		super.setLoggerName(this.loggerName + "._swConfigAlgo");
	}

	@Override
	public String toString() {
		Map<String, Object> fields = new HashMap<>();
		fields.put("planningGraphGeneratorDeriver", this.planningGraphGeneratorDeriver);
		fields.put("planningProblem", this.planningProblem);
		fields.put("search", this.search);
		fields.put("searchProblem", this.searchProblem);
		return ToJSONStringUtil.toJSONString(this.getClass().getSimpleName(), fields);
	}
}