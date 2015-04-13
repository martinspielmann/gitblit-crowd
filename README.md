GitBlit Crowd Integration
=========================

Integrates [Atlassian Crowd](http://www.atlassian.com/software/crowd/) with [GitBlit](http://gitblit.com).

[![Build Status](https://pingunaut.com/jenkins/buildStatus/icon?job=gitblit-crowd)](https://pingunaut.com/jenkins/job/gitblit-crowd/)

Features
--------

* Authenticates users against Crowd
* SSO
* Uses Crowd groups as GitBlit teams
* Allows defining which Crowd group(s) have GitBlit admin privileges

SSO
---

Crowd's single sign-on works fine (login and logout) with versions 1.0-0 and up.

Why can't I add/edit users and teams from GitBlit?
--------------------------------------------------

It's currently not implemented and probably never will. When using Crowd, it's probably a better idea to manage your users and groups there and then use them in GitBlit.
 

