# Change Log

# Change Log
All notable changes to this project will be documented in this file.
This project adheres to [Semantic Versioning](http://semver.org/).

## [0.1.7] - 2016-09-15
* Changed output directory of CSD build so it doesn't conflict with parcel building using `sbt-cloudera-parcel`
* Don't include the main build artifact by default

## [0.1.5] - 2016-05-06
* CSD building must be explicitly enabled by setting `buildCsd := true` for a project
  (fixes an issue with SBT failing to load a project without a valid CSD)
* Changed `csdAddLibs` to `csdAddAux` (as only the `aux` directory is retained; turns out `libs` is useless)
* CSD is now published during the main `publish` task (no need for `csd publish`)
