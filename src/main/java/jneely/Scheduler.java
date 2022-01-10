package jneely.custommetricsdemo;

// SLF4J Logging factory and kv util for Structured logging
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import static net.logstash.logback.argument.StructuredArguments.kv;

// Java core libraries
import java.time.Duration;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

// Spring Framework scheduled tasks
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StopWatch;

// Prometheus Client Library dependancies.
import io.prometheus.client.Counter;
import io.prometheus.client.Gauge;
import io.prometheus.client.Histogram;
import io.prometheus.client.Summary;
import io.prometheus.client.exporter.HTTPServer;
import io.opentelemetry.api.trace.Span;
import io.prometheus.client.exemplars.ExemplarConfig;
import io.prometheus.client.hotspot.DefaultExports;

@Component
public class Scheduler {
  // Logger setup
  private final Logger logger = LoggerFactory.getLogger(this.getClass());

  // Prometheus client library objects
  private final io.prometheus.client.Gauge prometheusGauge;
  private final io.prometheus.client.Counter prometheusTraffic;
  private final io.prometheus.client.Counter prometheusErrors;
  private final io.prometheus.client.Summary prometheusTimer;
  private final io.prometheus.client.Histogram prometheusHistogram;

  public Scheduler() {
    // Logging test
    logger.debug("This is a DEBUG message");
    logger.info("This is an INFO message");
    logger.warn("This is a WARN message");
    logger.error("This is an ERROR message");
    logger.error("Exemplars enabled: {}", ExemplarConfig.isExemplarsEnabled());

    // Setup the default auto-instrumentation and the custom metrics used in
    // this demo with the Prometheus client_java library.
    DefaultExports.initialize();
    prometheusGauge = io.prometheus.client.Gauge.build()
      .namespace("custommetricsdemo")
      .name("gauge")
      .help("Test Prometheus Client Library gauge")
      .register();
    prometheusTraffic = io.prometheus.client.Counter.build()
      .namespace("custommetricsdemo")
      .name("traffic")
      .help("Test Prometheus Client Library traffic counter")
      .register();
    prometheusErrors = io.prometheus.client.Counter.build()
      .namespace("custommetricsdemo")
      .name("errors")
      .help("Test Prometheus Client Library errors counter")
      .withExemplars()
      .register();
    prometheusTimer = io.prometheus.client.Summary.build()
      .namespace("custommetricsdemo")
      .name("latency_timer")
      .help("Test Prometheus Client Library latency summary")
      .quantile(0.5, 0.01)
      .quantile(0.95, 0.01)
      .register();
    prometheusHistogram = io.prometheus.client.Histogram.build()
      .namespace("custommetricsdemo")
      .name("histogram")
      .help("Test Prometheus Client Library latency histogram")
      .withExemplars()
      .register();

    try {
      io.prometheus.client.exporter.HTTPServer server
        = new io.prometheus.client.exporter.HTTPServer(8081);
    } catch (Exception e) {
      logger.error("Failed to setup Prometheus HTTP server", e);
    }
  }

  @Scheduled(fixedRateString = "1000", initialDelayString = "0")
  public void schedulingTask() {
    StopWatch sw = new StopWatch();
    Span span = Span.current();
    int success = 0;
    int r = this.getRandomNumberInRange(0, 750);

    // Setup MDC as if this was a job for a tenant/user
    MDC.put("tid", "1234");                                 // tid = tenant ID
    MDC.put("uid", "jneely");                               // uid == username
    MDC.put("jid", "job-9876");                             // jid == job ID
    MDC.put("cid", "DB93F282-5559-49B8-9BBB-F24E0086FE14"); // cid == Customer ID

    // 4 Golden Signals: The Traffic Counter
    prometheusTraffic.inc();
    // Set gauge to the random number/delay factor
    prometheusGauge.set(r);

    // Run the task and record its duration in milliseconds
    sw.start("task foobar");
    try {
        this.wait(r);
        sw.stop();
        // Add custom attributes to the Span including high cardinality data.
        span.setAttribute("custom.attribute", r);
        success = 1;
    } catch (Exception e) {
        sw.stop();
        // 4 Golden Signals: The Errors counter.  Use explicit Exemplars to
        // test that support with a high cardinality data tag (the span).
        prometheusErrors.incWithExemplar("span_foo", span.getSpanContext().getSpanId(), "trace_bar", span.getSpanContext().getTraceId());
        logger.error("Exception", e);
        span.recordException(e);
    }
    long milli = sw.getTotalTimeMillis();

    // 4 Golden Signals: The Duration distribution.
    // Summary type: no Exemplar support
    prometheusTimer.observe(sw.getTotalTimeSeconds());

    // Histogram type: Testing manual / explicit Exemplar support.
    prometheusHistogram.observeWithExemplar(sw.getTotalTimeSeconds(), "span_foo", "0xdeadbeef", "trace_bar", "DEADBEEF");

    // Create the Event Record with high cardinality data
    logger.info("task complete {} {}", kv("random_int", r),
        kv("success", success), kv("status", 200), kv("duration", milli));
    MDC.clear();
  }

  private void wait(int ms) {
    // Test exception handling in logs by blowing up every few tasks
    if (ms % 5 == 0) {
      ms = this.getRandomNumberInRange(750, 500); // IllegalArgumentException
    }

    // Run task
    try {
      Thread.sleep(ms);
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
    }
  }

  private int getRandomNumberInRange(int min, int max) {
    if (min >= max) {
      throw new IllegalArgumentException("max must be greater than min");
    }

    Random r = new Random();
    return r.nextInt((max - min) + 1) + min;
  }
}
