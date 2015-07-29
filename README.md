emem
======================================================================

A trivial Markdown to HTML converter that uses

* [markdown-clj](https://github.com/yogthos/markdown-clj)
* [hiccup](https://github.com/weavejester/hiccup)
* [tools.cli](https://github.com/clojure/tools.cli)
* [fs](https://github.com/raynes/fs/)
* [cpath-clj](https://github.com/xsc/cpath-clj)
* [highlight.js](https://github.com/isagalaev/highlight.js)

## Installation

    git clone git@github.com:ebzzry/emem.git
    cd emem && lein uberjar
    mkdir ~/jars && cp target/uberjar+uberjar/emem-0.1.1-SNAPSHOT.jar ~/jars/emem.jar

## Usage

*emem* is typically ran from the jar:

    java -jar ~/jars/emem.jar -o README.html README.md

To save typing, you may use shell functions:

    cat >> ~/.bashrc << END
    function emem () { java -jar ~/jars/emem.jar $@; }
    function em () { emem -o ${1%%.*}.html $1; }
    END

or a shell script:

    cat > ~/bin/emem <<
    #!/bin/sh
    java -jar ~/jars/emem.jar $@
    END
    chmod +x ~/bin/emem

Enabling us to just type:

    emem -o README.html README.md

or

    em README.md

Examples can be found in the `examples/` directory.


## Options

    Usage: emem [OPTION]... [MARKDOWN_FILE]...
    
    Options:
      -o, --output HTML_FILE  /dev/stdout  output file
      -t, --title TITLE                    document title
      -H, --header HEADER                  document header
      -T, --titlehead TEXT                 like -t TEXT -H TEXT
      -r                                   install the resource files only
      -R                                   build the HTML file only
      -v                                   increase verbosity
      -h, --help                           display this help


## Bugs


## License

Copyright © 2015 Rommel Martinez

Distributed under the Eclipse Public License
