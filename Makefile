.PHONY: all
all:

.PHONY: repl
repl:
	clj -M:dev:repl

.PHONY: test
test:
	clojure -M:dev -m cognitect.test-runner

.PHONY: lint
lint:
	clj-kondo --lint src test

.PHONY: fmt
fmt:
	cljstyle fix

.PHONY: clean
clean:
	rm -rf .cpcache target
