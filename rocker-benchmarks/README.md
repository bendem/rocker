```bash
java -jar target/benchmarks.jar -jvmArgs '-Xmx5M' -rf json -rff 'jmh-output-5M.json' -si false -gc true -f 2 -w 5 -r 5
java -jar target/benchmarks.jar -jvmArgs '-Xmx100M' -rf json -rff jmh-output-100M.json -f 2 -w 5 -r 5
```
