alfred
======

![Logo](http://www.dosarrest.com/templates/corporative/images/logo.gif "Logo")

Alfred, the ElasticSearch Butler created by Colton McInroy with [DOSarrest Internet Security](http://www.dosarrest.com/)

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

Alfred was built as a tool to handle maintenance work on ElasticSearch.
Alfred will delete, flush cache, optimize, close/open, enable/disable bloom filter, as well as put settings on indexes.
Alfred can do any of these actions based on either time or size parameters.

Examples:
```
java -jar alfred.jar -e48 -i"cron_*" -d
```
Delete any indexes starting with "cron_" that are older that 48 hours
```
java -jar alfred.jar -e24 -i"cron_*" -S'{"index.routing.allocation.require.tag":"historical"}'
```
Set routing to require historical tag on any indexes starting with "cron_" that are older that 24 hours
```
java -jar alfred.jar -e24 -i"cron_*" -b -o
```
Disable boom filter and optimize any indexes starting with "cron_" that are older that 24 hours
```
java -jar alfred.jar -ssize -E"1 GB" -d
```
Find all indxes, group by prefix, and delete indexes over a limit of 1 GB. Using the size style with an expire size does not check space based on a single index but rather the indexes adding up over time. Such as the following...
```
java -jar alfred.jar -i"cron_*" -d -ssize -E"500 GB"
GENERAL: cron_2014_04_02_08 is 469.9 GiB bytes before the cuttoff.
GENERAL: cron_2014_04_02_07 is 436.5 GiB bytes before the cuttoff.
GENERAL: cron_2014_04_02_06 is 404.0 GiB bytes before the cuttoff.
GENERAL: cron_2014_04_02_05 is 372.1 GiB bytes before the cuttoff.
GENERAL: cron_2014_04_02_04 is 341.2 GiB bytes before the cuttoff.
GENERAL: cron_2014_04_02_03 is 310.1 GiB bytes before the cuttoff.
GENERAL: cron_2014_04_02_02 is 276.8 GiB bytes before the cuttoff.
GENERAL: cron_2014_04_02_01 is 240.7 GiB bytes before the cuttoff.
GENERAL: cron_2014_04_02_00 is 202.2 GiB bytes before the cuttoff.
GENERAL: cron_2014_04_01_23 is 158.2 GiB bytes before the cuttoff.
GENERAL: cron_2014_04_01_22 is 110.6 GiB bytes before the cuttoff.
GENERAL: cron_2014_04_01_21 is 58.6 GiB bytes before the cuttoff.
GENERAL: cron_2014_04_01_20 is 3.1 GiB bytes before the cuttoff.
GENERAL: Index cron_2014_04_01_19 would have been deleted.
GENERAL: Index cron_2014_04_01_18 would have been deleted.
GENERAL: Index cron_2014_04_01_17 would have been deleted.
GENERAL: Index cron_2014_04_01_16 would have been deleted.
GENERAL: Index cron_2014_04_01_15 would have been deleted.
GENERAL: Index cron_2014_04_01_14 would have been deleted.
GENERAL: Index cron_2014_04_01_13 would have been deleted.
GENERAL: Index cron_2014_04_01_12 would have been deleted.
GENERAL: Index cron_2014_04_01_11 would have been deleted.
GENERAL: Index cron_2014_04_01_10 would have been deleted.
GENERAL: Index cron_2014_04_01_09 would have been deleted.
GENERAL: Index cron_2014_04_01_08 would have been deleted.
GENERAL: Index cron_2014_03_29_08 would have been deleted.
```

If you are using daily indexes, such as the marvel indexes, you could use the following examples to manage them
```
java -jar alfred.jar -i".marvel-*" -d -ssize -E"500 GB"
```
Keep the past 500 GB worth of marvel indices
```
java -jar alfred.jar -i".marvel-*" -d -T"day" -e7
```
Delete marvel indices older than 7 days old
```
java -jar alfred.jar -i".marvel-*" -b -o -T"day" --max_num_segments=4 -e1
```
Disable bloom filter and optimize marvel indices with max_num_segments=4 over 1 day old



The following regular expression is used to split indexes into appropriate variables...
```
^((?<Name>[a-zA-Z0-9\\.\\-_]+)(?<PrefixSeparator>(_|-)+)(?<Year>[0-9]{4})(?<Separator>(\\.|_|-))(?<Month>[0-9]{2})(\\.|_|-)(?<Day>[0-9]{2})(\\.|_|-)?(?<Hour>[0-9]{2})?)$
```
As long as your indexes following the pattern of this regular expression, Alfred will be glad to manage your indices.


Inspired by [curator](https://github.com/elasticsearch/curator)
