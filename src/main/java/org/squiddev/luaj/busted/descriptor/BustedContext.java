package org.squiddev.luaj.busted.descriptor;

import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.ThreeArgFunction;
import org.luaj.vm2.lib.ZeroArgFunction;
import org.squiddev.luaj.busted.BustedRunner;
import org.squiddev.luaj.busted.ITestItem;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Injects variables into a scope
 */
public class BustedContext {
	public enum EnvironmentType {
		/**
		 * Use parent environment
		 */
		Parent,

		/**
		 * Clone the global table
		 */
		Isolate,

		/**
		 * Store variables in the table above
		 */
		Unwrap,

		/**
		 * Use the parent environment via metatables
		 */
		Wrap,

		/**
		 * Store variables in the table 2 above
		 */
		Expose,
	}

	/**
	 * The runner for this BustedContext
	 */
	public final BustedRunner runner;

	/**
	 * The parent context
	 */
	public final BustedContext parent;

	/**
	 * The current environment type
	 */
	public final EnvironmentType type;

	/**
	 * Additional items to run
	 */
	public final Map<String, IBustedExecutor> descriptors = new HashMap<>();

	public final List<ITestItem> tests = new ArrayList<>();

	/**
	 * Randomize the order of the tests
	 */
	public boolean randomize = false;

	public final HashMap<String, Boolean> descriptorSuccess = new HashMap<>();

	private LuaValue env;

	public BustedContext(BustedContext parent, EnvironmentType type) {
		this.runner = parent.runner;
		this.parent = parent;
		this.type = type;
	}

	public BustedContext(BustedRunner runner) {
		this.runner = runner;
		this.parent = this;
		this.type = EnvironmentType.Parent;
	}

	/**
	 * Create a new environment that references an environment 'n' levels above
	 *
	 * @param levels Number of levels to go up
	 * @return The created environment
	 */
	private LuaTable unwrap(int levels) {
		BustedContext parent = this;
		for (int i = 0; i < levels; i++) {
			parent = parent.parent;
		}

		final BustedContext p = parent;

		LuaTable metaEnv = new LuaTable();
		metaEnv.set("__newindex", new ThreeArgFunction() {
			@Override
			public LuaValue call(LuaValue self, LuaValue key, LuaValue value) {
				(p.getEnv()).set(key, value);
				return LuaValue.NIL;
			}
		});
		metaEnv.set("__index", this.parent.getEnv());

		LuaTable env = new LuaTable();
		env.setmetatable(metaEnv);
		return env;
	}

	/**
	 * Get the environment for this context
	 *
	 * @return The environment for this context
	 */
	public LuaValue getEnv() {
		if (env == null) {
			switch (type) {
				case Wrap: {
					LuaTable env = new LuaTable();
					LuaTable metaEnv = new LuaTable();
					metaEnv.set(LuaValue.INDEX, parent.getEnv());
					env.setmetatable(metaEnv);
					return this.env = env;
				}
				case Isolate:
					// TODO: Clone global table
					return parent.getEnv().get("_G");
				case Unwrap:
					return env = unwrap(1);
				case Expose:
					return env = unwrap(2);
				case Parent:
					if (parent == this) throw new IllegalStateException("This item has no parent");
					return env = parent.env;
			}
		}

		return env;
	}

	/**
	 * Bind the busted environment to this
	 */
	public void setup() {
		runner.busted.bind(this);
	}

	/**
	 * Disable a function in this environment
	 *
	 * @param descriptor The name of the function to use
	 */
	public void reject(final String descriptor) {
		LuaValue env = getEnv();
		if (!env.get(descriptor).isnil()) {
			env.rawset(descriptor, new ZeroArgFunction() {
				@Override
				public LuaValue call() {
					throw new LuaError("'" + descriptor + "' not supported inside current context block");
				}
			});
		}
	}

	/**
	 * Disable all busted functions
	 */
	public void rejectAll() {
		for (String descriptor : runner.busted.executors.items.keySet()) {
			reject(descriptor);
		}
	}

	/**execute(descriptor, propagate);
	 * Execute the parent executor then this executor
	 *
	 * @param descriptor The name of the item to run
	 * @param propagate Call the parent context
	 */
	public void execute(String descriptor, boolean propagate) {
		if(propagate) parent.execute(descriptor, true);
		execute(descriptor);
	}

	/**
	 * Execute this executor then the parent
	 *
	 * @param descriptor The name of the item to run
	 * @param propagate Call the parent context
	 */
	public void executeReverse(String descriptor, boolean propagate) {
		execute(descriptor);
		if(propagate) parent.executeReverse(descriptor, true);
	}

	/**
	 * Execute a descriptor
	 * @param descriptor The name of the descriptor
	 */
	protected void execute(String descriptor) {
		IBustedExecutor item = descriptors.get(descriptor);

		// Provide feedback on this item
		if (descriptorSuccess.get(descriptor) == null) descriptorSuccess.put(descriptor, true);

		if (item != null) {
			try {
				item.invoke(this);
			} catch(Exception e) {
				descriptorSuccess.put(descriptor, false);
				throw new RuntimeException(e);
			}
		}
	}
}
