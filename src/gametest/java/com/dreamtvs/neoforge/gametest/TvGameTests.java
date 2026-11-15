package com.dreamtvs.neoforge.gametest;

import com.dreamtvs.DreamTvs;
import net.minecraft.gametest.framework.GameTestHelper;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

//? if >=1.21.5 {
/*import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
//? if >=26.1
//import net.minecraft.world.level.Level;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
*///?} else {
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.TestFunction;
import net.neoforged.neoforge.gametest.GameTestHolder;

import java.util.Collection;
//?}

/**
 * Registers every {@link TvTest} method of {@link #CLASSES} (plan §8). Up to Minecraft 1.21.4 tests are
 * {@code TestFunction}s from a {@code @GameTestGenerator}; since 1.21.5 they are test functions plus test
 * instances in registries, and each batch is its own (empty) test environment. Test ids are the method names in
 * snake case, e.g. {@code dreamtvs:rectangle_forms_one_screen}.
 */
//? if >=1.21.5 {
/*@Mod(DreamTvs.MOD_ID)
*///?} else
@GameTestHolder(DreamTvs.MOD_ID)
public final class TvGameTests {
    static final List<Class<?>> CLASSES = List.of(ScreenGameTests.class, CompatRecipeGameTests.class);
    private static final String DEFAULT_BATCH = "default";

    /** One {@link TvTest} method. */
    private record Test(String name, String batch, int timeoutTicks, Method method) {
        void run(GameTestHelper helper) {
            try {
                method.invoke(null, helper);
            } catch (InvocationTargetException e) {
                if (e.getCause() instanceof RuntimeException runtime) throw runtime;
                if (e.getCause() instanceof Error error) throw error;
                throw new IllegalStateException(e.getCause());
            } catch (IllegalAccessException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    private static List<Test> tests() {
        List<Test> tests = new ArrayList<>();
        for (Class<?> type : CLASSES) {
            for (Method method : type.getDeclaredMethods()) {
                TvTest test = method.getAnnotation(TvTest.class);
                if (test == null) continue;
                if (!Modifier.isStatic(method.getModifiers()) || method.getParameterCount() != 1
                        || method.getParameterTypes()[0] != GameTestHelper.class) {
                    throw new IllegalStateException(method + ": a @TvTest must be static and take a GameTestHelper");
                }
                String batch = test.batch().isEmpty() ? DEFAULT_BATCH : test.batch();
                tests.add(new Test(snakeCase(method.getName()), batch, test.timeoutTicks(), method));
            }
        }
        return tests;
    }

    /** {@code rectangleFormsOneScreen} → {@code rectangle_forms_one_screen}. */
    private static String snakeCase(String name) {
        return name.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase(Locale.ROOT);
    }

    //? if >=1.21.5 {
    /*private static final DeferredRegister<Consumer<GameTestHelper>> FUNCTIONS =
            DeferredRegister.create(Registries.TEST_FUNCTION, DreamTvs.MOD_ID);
    private static final List<Test> TESTS = tests();

    static {
        for (Test test : TESTS) FUNCTIONS.register(test.name(), () -> test::run);
    }

    public TvGameTests(IEventBus modBus) {
        FUNCTIONS.register(modBus);
        modBus.addListener(TvGameTests::register);
    }

    private static void register(RegisterGameTestsEvent event) {
        //? if >=26.1 {
        /^Map<String, Holder<TestEnvironmentDefinition<?>>> environments = new HashMap<>();
        ^///?} else
        Map<String, Holder<TestEnvironmentDefinition>> environments = new HashMap<>();
        for (Test test : TESTS) {
            var environment = environments.computeIfAbsent(test.batch(),
                    batch -> event.registerEnvironment(DreamTvs.id(batch)));
            //? if >=26.1 {
            /^var data = new TestData<>(environment, Level.OVERWORLD, DreamTvs.id("empty"),
                    test.timeoutTicks(), 0, true, Rotation.NONE, false, 1, 1, false, 0);
            ^///?} else {
            var data = new TestData<>(environment, DreamTvs.id("empty"),
                    test.timeoutTicks(), 0, true, Rotation.NONE, false, 1, 1, false);
            //?}
            event.registerTest(DreamTvs.id(test.name()), new FunctionGameTestInstance(
                    ResourceKey.create(Registries.TEST_FUNCTION, DreamTvs.id(test.name())), data));
        }
    }
    *///?} else {
    @GameTestGenerator
    public static Collection<TestFunction> generate() {
        List<TestFunction> functions = new ArrayList<>();
        for (Test test : tests()) {
            functions.add(new TestFunction(test.batch(), test.name(), DreamTvs.id("empty").toString(),
                    test.timeoutTicks(), 0L, true, test::run));
        }
        return functions;
    }
    //?}
}
