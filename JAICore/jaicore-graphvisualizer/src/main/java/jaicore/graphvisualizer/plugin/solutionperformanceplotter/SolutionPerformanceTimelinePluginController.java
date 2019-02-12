package jaicore.graphvisualizer.plugin.solutionperformanceplotter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jaicore.basic.ScoredItem;
import jaicore.basic.algorithm.events.AlgorithmEvent;
import jaicore.basic.algorithm.events.SolutionCandidateFoundEvent;
import jaicore.graphvisualizer.events.gui.GUIEvent;
import jaicore.graphvisualizer.plugin.ASimpleMVCPluginController;
import jaicore.graphvisualizer.plugin.controlbar.ResetEvent;
import jaicore.graphvisualizer.plugin.timeslider.GoToTimeStepEvent;

public class SolutionPerformanceTimelinePluginController extends ASimpleMVCPluginController<SolutionPerformanceTimelinePluginModel, SolutionPerformanceTimelinePluginView> {
	
	private Logger logger = LoggerFactory.getLogger(SolutionPerformanceTimelinePlugin.class);

	public SolutionPerformanceTimelinePluginController(SolutionPerformanceTimelinePluginModel model, SolutionPerformanceTimelinePluginView view) {
		super(model, view);
	}

	@Override
	public void handleGUIEvent(GUIEvent guiEvent) {
		if (guiEvent instanceof ResetEvent || guiEvent instanceof GoToTimeStepEvent) {
			getModel().clear();
		}
	}

	@SuppressWarnings("unchecked")
	@Override
	public void handleAlgorithmEventInternally(AlgorithmEvent algorithmEvent) {
		if (algorithmEvent instanceof SolutionCandidateFoundEvent) {
			logger.debug("Received solution event {}", algorithmEvent);
			SolutionCandidateFoundEvent<?> event = (SolutionCandidateFoundEvent<?>)algorithmEvent;
			if (!(event.getSolutionCandidate() instanceof ScoredItem)) {
				logger.warn("Received SolutionCandidateFoundEvent {}, but the encapsulated solution {} is not a ScoredItem.", algorithmEvent, event.getSolutionCandidate());
				return;
			}
			ScoredItem<?> solution = (ScoredItem<?>) event.getSolutionCandidate();
			if (!(solution.getScore() instanceof Number)) {
				logger.warn("Received SolutionCandidateFoundEvent, but the score is of type {}, which is not a number.", solution.getScore().getClass().getName());
				return;
			}
			logger.debug("Adding solution to model and updating view.");
			getModel().addEntry((SolutionCandidateFoundEvent<? extends ScoredItem<? extends Number>>)event);
		}
		else
			logger.trace("Received and ignored irrelevant event {}", algorithmEvent);
	}

}
