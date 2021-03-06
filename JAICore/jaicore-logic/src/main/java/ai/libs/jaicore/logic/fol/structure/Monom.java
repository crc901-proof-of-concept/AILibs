package ai.libs.jaicore.logic.fol.structure;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import ai.libs.jaicore.basic.sets.SetUtil;
import ai.libs.jaicore.logging.ToJSONStringUtil;

public class Monom extends LiteralSet {

	public static Monom fromCNFFormula(final CNFFormula formula) {
		Monom m = new Monom();
		for (Clause c : formula) {
			if (c.size() > 1) {
				throw new IllegalArgumentException("Monom constructor says: Cannot create monom from CNF with disjunctions " + formula);
			}
			m.addAll(c);
		}
		return m;
	}

	public Monom() {
		super();
	}

	public Monom(final Literal l) {
		super(l);
	}

	public Monom(final String literals) {
		super(literals, "&");
	}

	public Monom(final Collection<Literal> set) {
		this(set, true);
	}

	public Monom(final Collection<Literal> set, final boolean deep) {
		super(set, deep);
	}

	public Monom(final Collection<Literal> literals, final Map<? extends LiteralParam, ? extends LiteralParam> mapping) {
		super(literals, mapping);
	}

	/**
	 *
	 */
	private static final long serialVersionUID = 1279300062766067057L;

	@Override
	public String toString() {
		Map<String, Object> fields = new HashMap<>();
		fields.put("literals", this);
		return ToJSONStringUtil.toJSONString(this.getClass().getSimpleName(), fields);
	}

	public boolean isContradictory() {
		return this.containsPositiveAndNegativeVersionOfLiteral() || this.containsGroundEqualityPredicateThatEvaluatesTo(false);
	}

	@Override
	public boolean isConsistent() {
		return !this.isContradictory();
	}

	/**
	 * @param conclusion
	 *            Another literal set that may be concluded by this literal set.
	 * @return True, if this literal set logically implies the conclusion literal set under any partial mapping.
	 */
	@Override
	public boolean implies(final LiteralSet conclusion) throws InterruptedException {
		// check all partial mappings for implication
		for (Map<VariableParam, VariableParam> mapping : SetUtil.allMappings(this.getVariableParams(), conclusion.getVariableParams(), false, false, false)) {
			if (new LiteralSet(this, mapping).containsAll(conclusion)) {
				return true; // implication mapping found
			}
		}

		return false; // no implying mapping
	}

	@Override
	public Map<VariableParam, VariableParam> getImplyingMappingThatMapsFromConclusionVarsToPremiseVars(final LiteralSet conclusion) throws InterruptedException {
		for (Map<VariableParam, VariableParam> mapping : SetUtil.allMappings(conclusion.getVariableParams(), this.getVariableParams(), false, false, false)) {
			if (this.containsAll(new LiteralSet(conclusion, mapping))) {
				return mapping; // implication mapping found
			}
		}

		return null; // no implying mapping
	}

	public CNFFormula asCNF() {
		CNFFormula formula = new CNFFormula();
		for (Literal l : this) {
			formula.add(new Clause(l));
		}
		return formula;
	}
}
