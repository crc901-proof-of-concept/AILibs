package jaicore.search.algorithms.standard.bestfirst.exceptions;

import jaicore.basic.algorithm.exceptions.ObjectEvaluationFailedException;

/**
 * Exception that can be thrown by SolutionEvaluators to indicate that some kind
 * of static analysis failed prior to the node evaluation. This can be useful,
 * if such events should be displayed on the GUI.
 * 
 * @author mirko
 *
 */
public class VerificationFailedException extends ObjectEvaluationFailedException {

	private static final long serialVersionUID = 1L;
	
	final ObjectEvaluationFailedException encapsulatedException;

	public VerificationFailedException(ObjectEvaluationFailedException e) {
		super(e, "Inner Verification failed");
		this.encapsulatedException = e;
	}

	public ObjectEvaluationFailedException getEncapsulatedException() {
		return encapsulatedException;
	}

}