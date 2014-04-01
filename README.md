alfred
======

![Logo](http://www.dosarrest.com/templates/corporative/images/logo.gif "Logo")

Alfred, the ElasticSearch Butler created by Colton McInroy with DOSarrest Internet Security

[Download Current](https://github.com/DOSarrest-Internet-Security/alfred/raw/master/builds/alfred-0.0.1.jar)

```
usage: alfred
 -b,--debloom                  Disable Bloom on Indexes
 -B,--bloom                    Enable Bloom on Indexes
 -c,--close                    Close Indexes
 -D,--debug <arg>              Display debug (debug|info|warn|error|fatal)
 -d,--delete                   Delete Indexes
 -E,--expiresize <arg>         Byte size limit  (Default 10 GB)
 -e,--expiretime <arg>         Number of time units old (Default 24)
    --examples                 Show some examples of how to use Alfred
 -f,--flush                    Flush Indexes
 -h,--help                     Help Page (Viewing Now)
    --host <arg>               ElasticSearch Host
 -i,--index <arg>              Index pattern to match (Default _all)
    --max_num_segments <arg>   Optimize max_num_segments (Default 2)
 -o,--optimize                 Optimize Indexes
 -O,--open                     Open Indexes
    --port <arg>               ElasticSearch Port
 -r,--run                      Required to execute changes on
                               ElasticSearch
 -s,--style <arg>              Clean up style (time|size) (Default time)
 -S,--settings <arg>           PUT settings
    --ssl                      ElasticSearch SSL
 -T,--time-unit <arg>          Specify time units (hour|day|none) (Default
                               hour)
 -t,--timeout <arg>            ElasticSearch Timeout (Default 30)
Alfred Version: 0.0.1
```

Inspired by [curator](https://github.com/elasticsearch/curator)
