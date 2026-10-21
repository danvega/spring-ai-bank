package dev.danvega.bank.observability;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.ai.tool.observation.ToolCallingObservationContext;
import org.springframework.stereotype.Component;

@Component
class ToolCallLogger implements ObservationHandler<ToolCallingObservationContext> {

    private static final Logger log = LoggerFactory.getLogger(ToolCallLogger.class);

    @Override
    public boolean supportsContext(Observation.Context context) {
        return context instanceof ToolCallingObservationContext;
    }

    @Override
    public void onStop(ToolCallingObservationContext context) {
        log.info("{} {}", context.getContextualName(), context.getToolCallArguments());
    }

}
