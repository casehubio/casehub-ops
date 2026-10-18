# UC04: Multi-Specialist Desk

**Topology companion:** [L04 Fleet Scaling](../../deployment/04-llm-fleet/)
**Prerequisites:** UC03

## Scenario

A support desk with multiple specialist agents — billing, technical, product.
Each agent has distinct expertise, personality, and knowledge base. Customer
queries are routed to the right specialist. The fleet scales by adding
specialties, not by scaling individual agents.

## What the topology level teaches

L04 shows **how** forEach scaling creates differentiated fleet instances.
This use case shows **why** — different problems need different specialists.
