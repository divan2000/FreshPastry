package org.urajio.freshpastry.rice.environment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.urajio.freshpastry.rice.Destructable;
import org.urajio.freshpastry.rice.environment.exception.ExceptionStrategy;
import org.urajio.freshpastry.rice.environment.exception.simple.SimpleExceptionStrategy;
import org.urajio.freshpastry.rice.environment.params.Parameters;
import org.urajio.freshpastry.rice.environment.params.simple.SimpleParameters;
import org.urajio.freshpastry.rice.environment.processing.Processor;
import org.urajio.freshpastry.rice.environment.processing.sim.SimProcessor;
import org.urajio.freshpastry.rice.environment.processing.simple.SimpleProcessor;
import org.urajio.freshpastry.rice.environment.random.RandomSource;
import org.urajio.freshpastry.rice.environment.random.simple.SimpleRandomSource;
import org.urajio.freshpastry.rice.environment.time.TimeSource;
import org.urajio.freshpastry.rice.environment.time.simple.SimpleTimeSource;
import org.urajio.freshpastry.rice.environment.time.simulated.DirectTimeSource;
import org.urajio.freshpastry.rice.selector.SelectorManager;

import java.io.IOException;
import java.util.HashSet;


/**
 * Used to provide properties, timesource etc to the FreePastry
 * apps and components.
 * <p>
 * XXX: Plan is to place the environment inside a PastryNode.
 *
 * @author Jeff Hoye
 */
public class Environment implements Destructable {
    private final static Logger logger = LoggerFactory.getLogger(Environment.class);

    public static final String[] defaultParamFileArray = {"freepastry"};
    private SelectorManager selectorManager;
    private Processor processor;
    private RandomSource randomSource;
    private TimeSource time;
    private Parameters params;
    private ExceptionStrategy exceptionStrategy;

    private HashSet<Destructable> destructables;

    /**
     * Constructor.  You can provide null values for all/any paramenters, which will result
     * in a default choice.  If you want different defaults, consider extending Environment
     * and providing your own chooseDefaults() method.
     *
     * @param sm   the SelectorManager.  Default: rice.selector.SelectorManager
     * @param rs   the RandomSource.  Default: rice.environment.random.simple.SimpleRandomSource
     * @param time the TimeSource.  Default: rice.environment.time.simple.SimpleTimeSource
     */
    public Environment(SelectorManager sm, Processor proc, RandomSource rs, TimeSource time, Parameters params, ExceptionStrategy strategy) {
        this.selectorManager = sm;
        this.randomSource = rs;
        this.time = time;
        this.params = params;
        this.processor = proc;
        this.exceptionStrategy = strategy;

        this.destructables = new HashSet<>();

        if (params == null) {
            throw new IllegalArgumentException("params cannot be null");
        }

        // choose defaults for all non-specified parameters
        chooseDefaults();

        this.selectorManager.setEnvironment(this);

        addDestructable(this.time);
    }

    /**
     * Convienience for defaults.
     *
     * @param paramFileName the file where parameters are saved
     */
    public Environment(String[] orderedDefaultFiles, String paramFileName) {
        this(null, null, null, null, new SimpleParameters(orderedDefaultFiles, paramFileName), null);
    }

    public Environment(String paramFileName) {
        this(defaultParamFileArray, paramFileName);
    }

    /**
     * Convenience for defaults.  Has no parameter file to load/store.
     */
    public Environment() {
        this(null);
    }

    // marked as deprecated to stop using insecure seeds
    @Deprecated
    public static Environment directEnvironment(long randomSeed) {
        RandomSource randomSource = new SimpleRandomSource(randomSeed);
        return directEnvironment(randomSource);
    }

    // TODO: null RandomSource? why???
    public static Environment directEnvironment() {
        return directEnvironment(null);
    }

    public static Environment directEnvironment(RandomSource rs) {
        Parameters params = new SimpleParameters(Environment.defaultParamFileArray, null);
        DirectTimeSource dts = new DirectTimeSource(params);
        SelectorManager selector = generateDefaultSelectorManager(dts, rs);
        dts.setSelectorManager(selector);
        Processor proc = new SimProcessor(selector);
        return new Environment(selector, proc, rs, dts, params, generateDefaultExceptionStrategy());
    }

    public static ExceptionStrategy generateDefaultExceptionStrategy() {
        return new SimpleExceptionStrategy();
    }

    public static RandomSource generateDefaultRandomSource(Parameters params) {
        RandomSource randomSource;
        if (params.contains("random_seed")) {
            randomSource = new SimpleRandomSource(params.getLong("random_seed"));
        } else {
            randomSource = new SimpleRandomSource();
        }
        return randomSource;
    }

    public static TimeSource generateDefaultTimeSource() {
        return new SimpleTimeSource();
    }

    public static SelectorManager generateDefaultSelectorManager(TimeSource time, RandomSource randomSource) {
        return new SelectorManager("Default", time, randomSource);
    }

    public static Processor generateDefaultProcessor() {
        return new SimpleProcessor("Default");
    }

    /**
     * Can be easily overridden by a subclass.
     */
    protected void chooseDefaults() {
        if (time == null) {
            time = generateDefaultTimeSource();
        }
        if (randomSource == null) {
            randomSource = generateDefaultRandomSource(params);
        }
        if (selectorManager == null) {
            selectorManager = generateDefaultSelectorManager(time, randomSource);
        }
        if (processor == null) {
            if (params.contains("environment_use_sim_processor") &&
                    params.getBoolean("environment_use_sim_processor")) {
                processor = new SimProcessor(selectorManager);
            } else {
                processor = generateDefaultProcessor();
            }
        }
        if (exceptionStrategy == null) {
            exceptionStrategy = generateDefaultExceptionStrategy();
        }
    }

    // Accessors
    public SelectorManager getSelectorManager() {
        return selectorManager;
    }

    public Processor getProcessor() {
        return processor;
    }

    public RandomSource getRandomSource() {
        return randomSource;
    }

    public TimeSource getTimeSource() {
        return time;
    }

    public Parameters getParameters() {
        return params;
    }

    /**
     * Tears down the environment.  Calls params.store(), selectorManager.destroy().
     */
    @Override
    public void destroy() {
        try {
            params.store();
        } catch (IOException ioe) {
            logger.warn("Error during shutdown", ioe);
        }
        if (getSelectorManager().isSelectorThread()) {
            callDestroyOnDestructables();
        } else {
            getSelectorManager().invoke(new Runnable() {
                @Override
                public void run() {
                    callDestroyOnDestructables();
                }
            });
        }
    }

    private void callDestroyOnDestructables() {
        for (Destructable destructable : destructables) {
            destructable.destroy();
        }
        selectorManager.destroy();
        processor.destroy();
    }

    public void addDestructable(Destructable destructable) {
        if (destructable == null) {
            logger.warn("addDestructable(null)", new Exception("Stack Trace"));
        } else {
            destructables.add(destructable);
        }
    }

    public void removeDestructable(Destructable destructable) {
        if (destructable == null) {
            logger.warn("removeDestructable(null)", new Exception("Stack Trace"));
        } else {
            destructables.remove(destructable);
        }
    }

    public ExceptionStrategy getExceptionStrategy() {
        return exceptionStrategy;
    }

    /**
     * Replace Exception strategy, return old strategy
     *
     * @param newStrategy
     * @return old strategy
     */
    public ExceptionStrategy setExceptionStrategy(ExceptionStrategy newStrategy) {
        ExceptionStrategy oldStrategy = exceptionStrategy;
        exceptionStrategy = newStrategy;
        return oldStrategy;
    }

    public Environment cloneEnvironment(String prefix) {
        return cloneEnvironment(prefix, false, false);
    }

    public Environment cloneEnvironment(String prefix, boolean cloneSelector, boolean cloneProcessor) {

        TimeSource ts = cloneTimeSource();

        // new random source
        RandomSource rand = cloneRandomSource();

        // new selector
        SelectorManager sman = cloneSelectorManager(prefix, ts, rand, cloneSelector);

        // new processor
        Processor proc = cloneProcessor(prefix, cloneProcessor);

        // build the environment
        Environment ret = new Environment(sman, proc, rand, getTimeSource(), getParameters(), getExceptionStrategy());

        // gain shared fate with the rootEnvironment
        addDestructable(ret);

        return ret;
    }

    protected TimeSource cloneTimeSource() {
        return getTimeSource();
    }

    protected SelectorManager cloneSelectorManager(String prefix, TimeSource ts, RandomSource rs, boolean cloneSelector) {
        SelectorManager sman = getSelectorManager();
        if (cloneSelector) {
            sman = new SelectorManager(prefix + " Selector", ts, rs);
        }
        return sman;
    }

    protected Processor cloneProcessor(String prefix, boolean cloneProcessor) {
        Processor proc = getProcessor();
        if (cloneProcessor) {
            proc = new SimpleProcessor(prefix + " Processor");
        }
        return proc;
    }

    protected RandomSource cloneRandomSource() {
        return new SimpleRandomSource();
    }
}

