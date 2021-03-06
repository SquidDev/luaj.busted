package org.squiddev.luaj.busted.blocks;

import org.junit.runners.model.InitializationError;
import org.junit.runners.model.MultipleFailureException;
import org.junit.runners.model.Statement;
import org.squiddev.luaj.busted.ITestItem;
import org.squiddev.luaj.busted.TestItemRunner;
import org.squiddev.luaj.busted.descriptor.BustedContext;

import java.util.Collections;
import java.util.List;

/**
 * Super class for all items that call items
 */
public abstract class Block extends TestItemRunner<ITestItem> {
	protected final BustedContext context;

	protected Block(BustedContext context) throws InitializationError {
		super(context.runner.getTestClass().getJavaClass());
		this.context = context;
	}

	/**
	 * Returns a {@link Statement}: run all non-overridden {@code @BeforeClass} methods on this class
	 * and superclasses before executing {@code statement}; if any throws an
	 * Exception, stop execution and pass the exception on.
	 *
	 * @param statement The resulting statement
	 */
	@Override
	protected Statement withBeforeClasses(final Statement statement) {
		return super.withBeforeClasses(new Statement() {
			@Override
			public void evaluate() throws Throwable {
				context.execute("strict_setup", false);

				statement.evaluate();
			}
		});
	}

	/**
	 * Returns a {@link Statement}: run all non-overridden {@code @AfterClass} methods on this class
	 * and superclasses before executing {@code statement}; all AfterClass methods are
	 * always executed: exceptions thrown by previous steps are combined, if
	 * necessary, with exceptions from AfterClass methods into a
	 * {@link MultipleFailureException}.
	 *
	 * @param statement The statement to execute
	 */
	@Override
	protected Statement withAfterClasses(final Statement statement) {
		return super.withAfterClasses(new Statement() {
			@Override
			public void evaluate() throws Throwable {
				statement.evaluate();

				context.executeReverse("strict_teardown", false);
				context.executeReverse("lazy_teardown", false);
			}
		});
	}

	/**
	 * Returns a list of objects that define the children of this Runner.
	 */
	@Override
	protected List<ITestItem> getChildren() {
		List<ITestItem> children = super.getChildren();
		if (context.randomize) Collections.shuffle(children);

		return children;
	}
}
