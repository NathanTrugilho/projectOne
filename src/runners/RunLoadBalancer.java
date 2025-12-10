package runners;

import loadbalancer.LoadBalancer;

public class RunLoadBalancer {
    public static void main(String[] args) {
        System.out.println("Iniciando Load Balancer...");
        new Thread(new LoadBalancer()).start();
    }
}