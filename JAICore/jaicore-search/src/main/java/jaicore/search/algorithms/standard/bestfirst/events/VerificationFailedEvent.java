package jaicore.search.algorithms.standard.bestfirst.events;

import java.util.List;

import jaicore.search.algorithms.standard.bestfirst.exceptions.VerificationFailedException;

/**
 * Indicates that the given path threw the encapsulated
 * {@link VerificationFailedException}.
 * 
 * This can be used by GUI-listeners to notify the user that an incompatible composition has been found.
 * 
 * @author mirko
 *
 * @param <T> usually TFDNode, but generally the type of the nodes in the path
 */
public class VerificationFailedEvent<T> {

	public VerificationFailedException getE() {
		return e;
	}

	public List<T> getPathToSolution() {
		return pathToSolution;
	}

	public VerificationFailedEvent(VerificationFailedException e, List<T> pathToSolution) {
		super();
		this.e = e;
		this.pathToSolution = pathToSolution;
	}

	private VerificationFailedException e;

	private List<T> pathToSolution;

}