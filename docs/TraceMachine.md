# Trace Machine (Traces and Interactions)

Interactions and traces follow the threading of the app. If the app is complex, traces will be as well, which is why having more data is better. Log data should include data from before the app starts, to 30 seconds after it has backgrounded.

Agent data harvests occur every 60 seconds, and any traces created since the last harvest will be stopped. If an app starts a trace, but the code does nothing else that would trigger an update, that trace would report _duration_ as the difference between its start time and the current harvest time.

Actions that update trace state include:
* return from a method
* start a new activity or fragment
* make a network request
* calling the static API NewRelic.endInteraction()


## TraceMachine
* Requires FeatureFlag.InteractionTracing
* There is a single TraceMachine
	* startTracing() replaces TraceMachine singleton, and connects activityTrace to new TraceMachine
* There is a single TraceMachineInterface
* There is a single activityHistory list
* Single ThreadLocal trace and ThreadLocal trace stack
* TraceMachine is highly contentious: all running threads can impact state of traces

### Typical trace sequence:
1. Start trace
	* Called automatically through code injection, and manually through API
2. For all child traces:
	* enter method
	* exit method
3. End trace
	* `endTrace` is only called directly from AsyncTask.onPostExecute

### HEALTHY_TRACE_TIMEOUT
The minimum trace duration, as the time since the trace's last last update. Timed-out traces are completed.
Default: 500 ms.

### UNHEALTHY_TRACE_TIMEOUT
The maximum trace duration, as the time since the trace's creation. Timed-out traces are completed.
Default: 60000 ms. (1 minutes, or harvest period)

## Trace
* Contains trace start/end times
* Contains child traces
* Completed traces are added to current ActivityTrace

### Child trace
Any trace in another thread started automatically (through injection) or manually (@Trace annotation or API)

## ActivityTrace
* TraceMachine has a single ActivityTrace
* ActivityTrace must have root trace
* ActivityTraces are completed if another trace is started before the current completes
* ActivityTrace is completed if time since last update is > HEALTHY_TRACE_TIMEOUT (0.5s) and has no pending child traces, or time since trace inception is > UNHEALTHY_TRACE_TIMEOUT (60s)
	* applies when a new method is entered, and immediately before a harvest starts
* ActivityTrace is not recorded if it has no children, or child exclusive time < 33% of total trace duration
* If activityTrace limit (1) is reached, all further traces are dropped:
	* "Activity trace limit of 1 exceeded. Ignoring trace: "


	* complete current ActivityTrace if a trace is running
	* creates a new rootTrace
	* replaces TraceMachine singleton
	* connects TraceMachine to new ActivityTrace

	```
		rootTrace.threadLocalTrace = ThreadLocal<Trace>
		rootTrace.threadLocalTraceStack = ThreadLocal<TraceMachine.TraceStack>
		rootTrace.activityHistory = List<ActivitySighting>
		rootTrace.UUID = "628b5a51-521d-2178-6c15-ed1069a8684e"
		rootTrace.displayName = "Display MainActivity"
		rootTrace.metricName = "Mobile/Activity/Name/Display MainActivity"
		rootTrace.metricBackgroundName = "Mobile/Activity/Background/Name/Display MainActivity"
		rootTrace.entryTimestamp : root trace is created
		rootTrace.traceMachine = new TraceMachine(rootTrace)

		pushTraceContext(rootTrace);

		rootTrace.traceMachine.activityTrace.previousActivity = getLastActivitySighting();
		activityHistory.add(new ActivitySighting(rootTrace.entryTimestamp, rootTrace.displayName));
		Measurements.startActivity(rootTrace.displayName)
		addHarvestListener(this)
	```


## Class rewriter
TraceMethodVisitor injects calls to `TraceMachine.startTracing({activityName})` and ` ApplicationStateMonitor.onStart()` into the start of all classes derived from Android Activity and Fragment classes. Traces are injected into the following overridden class methods:
* Activity.onCreate()
* Activity.onCreateView
* Fragment.onCreate()
* Fragment.onCreateView

ActivityTraces are completed if another trace is started before the current completes
ActivityTraces are completed on harvest (every ~60 secs), regardless of when it was started
ActivityTraces are expired if could not be reported in harvest

If activityTrace limit (1) is reached, all further traces are dropped
* ```Activity trace limit of 1 exceeded. Ignoring trace: ```

Class rewriter *does not* inject calls to `endTrace`: allows runtime behavior to shut down running traces through preemption and timeouts


## Method Tracing

categoryParams = ["category", MetricCategory.class.getName(), "JSON"]

Traces can be started manually through @Trace annotation, or method calls:

```
TraceMachine.enterMethod("Class#method", categoryParams);
String string = gson.toJson(src, typeOfSrc);
TraceMachine.exitMethod();
```

## Activity injection
Class rewriter also injects call to `TraceMachine.enterMethod("Activity#onCreate")` into start of all Activity.onCreate()/Fragment.onCreate() overrides.

### Prune healthy timeouts that have no missing children
if (currentTrace.lastUpdatedAt + (long)HEALTHY_TRACE_TIMEOUT < currentTime && activityTrace has no missing children
	complete Activitytrace
	* "missing children" - subtraces that have not completed

// Prune unhealthy timeouts regardless of missing children
if (currentTrace.inception + (long)UNHEALTHY_TRACE_TIMEOUT < currentTime)
	complete ActivityTrace


### Inject call to TrcaeMachine.exitMethod()
Class rewriter injects call to `TraceMachine.exitMethod(),activityName")` and `ApplicationStateMonitor.onStop()` into end of all Activity.onCreate()//Fragment.onCreate() overrides

Trace may have already been completed via HEALTHY_TRACE_TIMEOUT or preemption from another trace starting
current trace = threadLocalTrace
shutdown current trace

### Completing trace
* As each child trace (method) completes, total time is added to parent trace's '_exclusive time_'
* ActivityTrace is dropped if traceUtilization (activityTrace.childExclusiveTime /  activityTrace.duration) <= 0.333 for all child traces

Look in logs for:
```
Discarding trace of {trace display name}...
Completing trace of {trace display name}...
```

## Network Requests
* Current trace is renamed
> TraceMachine.setCurrentDisplayName("External/" + (new URL(url)).getHost())
* Any outstanding trace is ended when network request completes
> Network traces may never end because the stream isn't read or the response was cached.
In this case, current network traces are ended before starting another one.

## TraceFieldInterface
Class rewriter (`TraceClassDecorator`) injects `TraceFieldInterface` into traced methods

## TraceMachineInterface
Provides current thread context

## Trace Configuration
* Mostly not used

```
{
	"server_timestamp": 1601520537,
	"data_report_period": 60,
	"report_max_transaction_count": 1
	"report_max_transaction_age": 600,
	"collect_network_errors": true,
	"error_limit": 50,
	"response_body_limit": 2048,
	"stack_trace_limit": 100,
	"activity_trace_max_size": 65535,
	"activity_trace_min_utilization": 0.3,
	"at_capture": [1, []],
	"data_token": [52088176, 298381946],
	"cross_process_id": "XAUAWFFQGwYCVFlaBgYB",
	"encoding_key": "d67afc830dab717fd163bfcb0b8b88423e9a1a3b",
	"account_id": "837973",
	"application_id": "52088176"
}
```

> Outstanding Questions [TO-DO]:
* Interaction metrics vs InteractionEvents?
* How to you time interactions (custom tracking)?
* Does manually timed interaction contain time spent in children (threads)?
* Does time represent a complete ActivityTrace, ie. Activity.onCreate()?
* Is a network request involved in problem trace?
* Is Activity.onCreateView() considered in timing?
* Are long-running interaction is _blocked_ on child traces (not pruned due to timeouts)
