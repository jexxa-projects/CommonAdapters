package io.jexxa.common.drivingadapter.scheduler;

import io.jexxa.adapterapi.drivingadapter.IDrivingAdapter;
import io.jexxa.adapterapi.invocation.InvocationManager;
import io.jexxa.adapterapi.invocation.InvocationTargetRuntimeException;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static io.jexxa.common.facade.logger.SLF4jLogger.getLogger;


public class Scheduler implements IDrivingAdapter
{
    private final ScheduledExecutorService executorService = Executors.newScheduledThreadPool(2);
    private final Map<Object, List<Method>> scheduledMethods = new HashMap<>();

    private final List<IScheduled> scheduledObjects= new ArrayList<>();

    @Override
    public void register(Object port) {
        if (port instanceof IScheduled iScheduled) {
            scheduledObjects.add(iScheduled);
        } else {
            validateSchedulerConfiguration(port);
            scheduledMethods.put(port, getSchedulerConfiguration(port));
        }
    }

    @Override
    public void start() {
        scheduledMethods.forEach(this::registerScheduledMethods);
        scheduledObjects.forEach(this::registerScheduledObjects);
    }

    private void registerScheduledObjects(IScheduled iScheduled)
    {
        if (iScheduled.fixedRate() >= 0) {
            executorService.scheduleAtFixedRate(
                    () -> invoke(iScheduled),
                    iScheduled.initialDelay(),
                    iScheduled.fixedRate(),
                    iScheduled.timeUnit());
        } else {
            executorService.scheduleWithFixedDelay(
                    () -> invoke(iScheduled),
                    iScheduled.initialDelay(),
                    iScheduled.fixedDelay(),
                    iScheduled.timeUnit());
        }
    }
    private void registerScheduledMethods(Object port, List<Method> scheduledMethods)
    {
        scheduledMethods.forEach( method ->
        {
            var schedulerConfiguration = method.getAnnotation(Scheduled.class);
            if (schedulerConfiguration.fixedRate() >= 0) {
                executorService.scheduleAtFixedRate(
                        () -> invoke(method, port),
                        schedulerConfiguration.initialDelay(),
                        schedulerConfiguration.fixedRate(),
                        schedulerConfiguration.timeUnit());
            } else {
                executorService.scheduleWithFixedDelay(
                        () -> invoke(method, port),
                        schedulerConfiguration.initialDelay(),
                        schedulerConfiguration.fixedDelay(),
                        schedulerConfiguration.timeUnit());
            }
        });
    }

    private void invoke(Method method, Object port )
    {
        var invocationHandler = InvocationManager.getInvocationHandler(port);

        try {
            invocationHandler.invoke(method, port, new Object[0]);
        }
        catch (InvocationTargetRuntimeException e) {
            getLogger(port.getClass()).error(e.getTargetException().getMessage());
            getLogger(port.getClass()).debug(e.getTargetException().getMessage(), e.getTargetException());
        }
        catch (Exception e)
        {
            getLogger(port.getClass()).error(e.getMessage());
            getLogger(port.getClass()).info(e.getMessage(), e);
        }
    }

    private void invoke(IScheduled port )
    {
        var invocationHandler = InvocationManager.getInvocationHandler(port);

        try {
            invocationHandler.invoke(port, port::execute);
        }
        catch (InvocationTargetRuntimeException e) {
            getLogger(port.getClass()).error(e.getTargetException().getMessage());
            getLogger(port.getClass()).debug(e.getTargetException().getMessage(), e.getTargetException());
        }
        catch (Exception e)
        {
            getLogger(port.getClass()).error(e.getMessage());
            getLogger(port.getClass()).info(e.getMessage(), e);
        }
    }

    @Override
    public void stop() {
        executorService.shutdown();
        try
        {
            if (!executorService.awaitTermination(800, TimeUnit.MILLISECONDS))
            {
                executorService.shutdownNow();
            }
        }
        catch (InterruptedException e)
        {
            executorService.shutdownNow();
            getLogger(Scheduler.class).warn("ExecutorService could not be stopped -> Force shutdown.", e);
            Thread.currentThread().interrupt();
        }
    }

    private void validateSchedulerConfiguration(Object object)
    {
        if (object instanceof IScheduled)
        {
            return;
        }

        var scheduledConfiguration = getSchedulerConfiguration(object);
        if (scheduledConfiguration.isEmpty())
        {
            throw new IllegalArgumentException(
                    String.format("Given object %s does not provide a %s annotation for any public method!"
                            , object.getClass().getSimpleName()
                            , Scheduled.class.getSimpleName()));
        }

        var result = scheduledConfiguration
                .stream()
                .filter( element ->
                        (element.getAnnotation(Scheduled.class).fixedDelay() < 0 && element.getAnnotation(Scheduled.class).fixedRate() < 0)
                     || (element.getAnnotation(Scheduled.class).fixedDelay() >= 0 && element.getAnnotation(Scheduled.class).fixedRate() >= 0))
                .findAny();

        if (result.isPresent()) {
                throw new IllegalArgumentException(
                    String.format("Given method %s::%s does not provide a valid  value for `fixedInterval` or `fixedDelay` in @Scheduled (exact one of these values must be >=0)!"
                            , object.getClass().getSimpleName()
                            , result.get().getName()));
        }

    }

    private List<Method> getSchedulerConfiguration(Object object)
    {
        return Arrays.stream(object.getClass().getMethods())
                .filter(method -> method.isAnnotationPresent(Scheduled.class))
                .filter(method -> method.getParameterCount() == 0)
                .toList();
    }
}
