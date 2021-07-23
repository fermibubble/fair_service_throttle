#### Fair Service Throttle

##### Introduction:

In a typical distributed request-response system, each service has different scaling characteristics, failure modes, and over capacity behavior. It's often difficult for a service to know how much traffic it can send downstream. If it sends too little, then the throughput of the whole system is unnecessarily limited. If it sends too much, it overloads the downstream service. The reduction in successful throughput (*goodput*) under increased load is called *congestive collapse*.  

The typically proposed solutions for these problems are:

- **Exponential back-off**: In most implementations, back-off is applied within a request, between retries. Without exponential back-off, offered load on system would increase linearly with the backlog of work, and back-off is very successful at managing this problem. On the other hand, it doesn't really help with the problem where requests keep coming in a at a rate higher than the system can serve.
- **Circuit breakers**: There are many definitions of circuit breakers, but the most typical and successful ones enable graceful degradation, where some features of a service are disabled when their downstream, dependencies are down. On the other hand, circuit breakers aren't particularly successful at keeping a service running at it's maximum available throughput.
- **Backpressure**: This simply refers to pushing pressure from a slow or failing dependency upstream to services clients, and up from there. Backpressure is critical to well-conditioned systems, but isn't much help when clients are numerous or don''t respect the backpressure

The approach that should be used more often is **adaptive throttling**, where a client system adapts the rate it sends requests to a downstream service to try use all of it's available goodput, without sending it into congestive collapse.  



 ##### Adaptive Throttling and Fairness

The "fair_service_throttle" is an adaptive throttle designed for use between services. It offers adaptive throttling (within a single client process) which lets more and more traffic through when the downstream service is accepting it, and slows down the traffic it sends through when the downstream service starts failing requests. This adaptive control is based on a simple algorithm called [Additive Increase, Multiplicative Decrease (AIMD)](https://en.wikipedia.org/wiki/Additive_increase/multiplicative_decrease), similar to the approach used by TCP to adapt throughput on a single stream to the available bandwidth.

It also offers approximate fairness. In the kinds of multi-tenant systems we build, it's not acceptable to completely starve the traffic of any single customer or account. Instead, when there is limited goodput to go around, the limited goodput should be shared approximately evenly between the customers. It contains two fairness implementations, both offering approximate fairness with O(1) memory and work (an important property of something designed to protect systems under load). One is based on the idea from [Stochastic Fairness Queuing](http://www2.rdrop.com/users/paulmck/scalability/paper/sfq.2002.06.04.pdf) of mapping customers to a shared pool of throttle using a time-keyed hash. The other is based on a bloom filter of throttles, following ideas from [Stochastic Fair BLUE](https://ieeexplore.ieee.org/document/916648).  



