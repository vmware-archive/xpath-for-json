

# xpath-for-json

## Overview

This project is a utility to allow using XPath over JSON (a tool that I find very handy, that I built myself). But, JSONPath (another opensource project) is an alternative that is widely used (and is available in Java, Python and Scala). Incidentally, while JSONPath solves most of the cases, but is still buggy and does not support many corner cases  - which my tool solves. The development pace of JSONPath is slower, and it has some design flaws which may not get address at all (https://goessner.net/articles/JsonPath/#issues). Fortunately, most of the business in the world are not getting impacted by these flaws in the existing JSONPath project, as the alternatives and workarounds always exist.

This project, however, brings the goodness of good old XPath (that is used over XML) and all those research, into the JSON world, and is simple and intuitive. Having XPath syntax goes well with the community that is already well versed in the XPath-syntax and do not want another set of syntax of JSONPath to confuse them.

## Try it out

### Prerequisites

* Java 8
* Maven 3.2.3 (or higher)


### Build & Run

* mvn clean install
* mvn test

### Sample Code
        String xpath = "//your/xpath[filter]"; 
        JsonNode jn = getJsonNode(yourJsonStr);
        JsonNode resNode = JsonXpath.find(jn, xpath);
        LOG.info("{} - {}", xpath, resNode);

Please see the JUnit cases in TestJsonXpath.java within src/test/java

## Documentation

### Why
What the JSONPath "$..groupingObjectId[?(@ =~ /^ipset-[\\d]+$/)]" intends to do is, pick the values that matches 'ipset-XXXX' (X being a digit) for all the fields "groupingObjectId" occurring anywhere down in the input JSON document. Someone familier with XPath (https://www.w3.org/TR/1999/REC-xpath-19991116/) would expect that to be written somthing like:
    //groupingObjectId [ value.matches('^ipset-[\\d]+$') ]

This project aims at precisely that. The use of . and .. have been associated with a meaning that has been there from ages (and is widespread in FileSystems in various operating systems e.g. Windows, Unix, OSX) and to associate a different meaning to those in a different world makes it awkward to read and interpret (that's what happens with JSONPath, another opensource project). Similarly, the symbol '$' has a meaning in regular expressions, and to use it differently as a path-specifier often makes it non intuitive (the example above). There would be more such examples.

In addition, there are certain issues with JSONPath owing to its redefining of path symbols - "not supporting parent navigation" which is usually specified using a double-dot '..'. There are other limitations with JSONPath (and I believe, those are due to bugs that require fixes) - for instance, " $..groupingObjectId[ ?(@ =~ /^ipset-[\\d]+$/) ][ 1 ]" returns blank, however one would expect it to return the first occurrence in the list of matches.

### What's new here
Apart from the good old Xpath syntax, there are a few advantages using this project:
* Javascript like syntax can be used for filtering, i.e. expression within [ ]
* There are built-in functions to handle single and double quote intermix.
* One can add custom javascript routimes in the evaluator, and use it with the filter expression ([ ... ])
* The iteration is built over Visitor-Pattern. One can build custom visitors and select/delete/update/add values or JSON structures deep within an input document. 

Refer to the JUnit cases in TestJsonXpath.java for examples for each of those.

## Releases & Major Branches

## Contributing

The xpath-for-json project team welcomes contributions from the community. If you wish to contribute code and you have not
signed our contributor license agreement (CLA), our bot will update the issue when you open a Pull Request. For any
questions about the CLA process, please refer to our [FAQ](https://cla.vmware.com/faq). For more detailed information,
refer to [CONTRIBUTING.md](CONTRIBUTING.md).

## License
The Desired State Configuration Resources for VMware is distributed under the BSD-2.

For more details, refer to the BSD-2 License File.

