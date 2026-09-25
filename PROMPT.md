# Prompt History

## Request 1

1. Go through the current folder as codebase to get context
2. read README.md 
3. Add comments at the start of all major functions to help me understand what each one does
4. Give me an overall summary after you are done

## Request 2

Build this in python, and we need thorough testing to stress test, especially the edge cases of cache filling up and evicting it, potential race conditions, etc. Build a skeleton where we would implement the logic later, I will dictate the architecture design so leave it blank for now. Spin off a sub-agent to propose tests that you would write that we can use to stress test the LRU cache.

## Request 3

For the LRU cache itself, we need to implement with following -

we need a doubly linked list to easily keep track of which one is most recently used and move to the front

we need a hashmap to actually store the value of the cache

whenever a cache is accessed we will move it from linked list to the front of the list, hashmap remains unchanged

whenever size exceeds limit we remove from tail and also remove from hashmap, assuming we don't rely on lazy cleanup.

Implement this with a code agent while I review your test plan

## Request 4

Go ahead and implement the test plans

## Request 5

In reality, would we actually be able to test LRUCacheStressTest on our local environment? would 1M ops overwhelm the single local environment that we have here?
