.PHONY: all
all:

.PHONY: setup
setup:
	./setup.sh

.PHONY: init
init:
	cd transformer-dojo && ./dojo init --lang clojure --session-lang Japanese

.PHONY: test
test:
	cd transformer-dojo && ./dojo test

.PHONY: lint
lint:
	clj-kondo --lint transformer-dojo/work

.PHONY: fmt
fmt:
	cljstyle fix transformer-dojo/work

.PHONY: clean
clean:
	rm -rf .cpcache
