# Tasks Worker

## What is the problem

A Task is simply a running instance of a class implementing the Task interface. But what about scaling, error and
upgrade conditions:

- there is not enough capacity on a single node
- the task itself fails and should try to restart
- the process running the task is shutdown in a controlled way
- the process running the task shuts down in an uncontrolled way
- a new version of a task is published whilst tasks are in progress

One solution is the concept of a "Task Worker". This can allocate Tasks to an appropriate node in pool and monitor for
error conditions. Obviously this is a non trival problem which needs to broken down into a number of simpler, managable
problems.

## Allocating a Task to a Task Worker.

In order to understand this, lets start with typical client code

```kotlin
// get a task client (client side)
val taskClient = SimpleTaskClient(registry)

// call the client
val inMemoryLogging = LoggingChannelLocator.inMemory()
val clientContext = SimpleClientContext(loggingChannelLocator = inMemoryLogging)
val result = taskClient.execBlocking(
    clientContext,
    "mycorda.app.tasks.test.ListDirectoryTask",
    ".",
    StringList::class
)
```

Some important points here are:

* the client sets up some rules in the clientContext that must be passed to the server
* the input (payload) to the Task is always a single class.

All this information must be converted into a single request. As both clientContext and the payload must follow the
restrictions imposed by
[Really Simple Serialisation](https://github.com/mycordaapp/really-simple-serialisation#readme) it easy to convert this
to and from JSON (or any other supported format) anywhere in the stack.

All server side code follows a similar style:

* the client context and the payload are unpacked
* the server checks the request is valid and allowed:
    - is the task registered?
    - do the security principle(s) in the context have the necessary permission? (RBAC checks and so on)
    - does the payload type match that expected by the Task?
* if all these pass, find a place to run the Task. Realistically, there are two choices:
    - allocate a thread in the current process, build a local ExecutionContext and start the Task
    - post a request into a queue, where it can be acted on by a free worker. This worker will then effectively run the
      steps above, i.e. create a thread and so on. As the validity of the request has already been checked, the worker
      can simply take the request and action it

Building out a reliable worker pool will take effort, but it should fit withing one of the patterns defined in the
patterns library.  

## Restarting Tasks

If we can assume that the Task is fully idempotent(  most basic is to assume that the actual Task is fully idempotent, i.e. if is restarted 
after a failure it will figure our somehow where it was before and just carry on. 

## 
