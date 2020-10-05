#!/usr/bin/env bash
# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements.  See the NOTICE file distributed with
# this work for additional information regarding copyright ownership.
# The ASF licenses this file to You under the Apache License, Version 2.0
# (the "License"); you may not use this file except in compliance with
# the License.  You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
VERSION=$(mvn org.apache.maven.plugins:maven-help-plugin:2.1.1:evaluate -Dexpression=project.version | grep -v '\[')
PROJECT=$(mvn org.apache.maven.plugins:maven-help-plugin:2.1.1:evaluate -Dexpression=project.artifactId | grep -v '\[')
# Build first time
mvn clean verify -e -DskipTests artifact:buildinfo
mv target/$PROJECT-$VERSION.buildinfo /tmp
mv target/$PROJECT-$VERSION.jar /tmp
# Rebuild second time and compare builds
mvn clean verify -e -DskipTests artifact:buildinfo
mv /tmp/$PROJECT-$VERSION.buildinfo target/$PROJECT-$VERSION-org.buildinfo
mv /tmp/$PROJECT-$VERSION.jar target/$PROJECT-$VERSION-org.jar

# Strip out any filesystem disorder
strip-nondeterminism target/$PROJECT-$VERSION-org.jar
strip-nondeterminism target/$PROJECT-$VERSION.jar

# Use diffoscope to compare and verify builds are reproducible
diffoscope target/$PROJECT-$VERSION-org.jar target/$PROJECT-$VERSION.jar

exitCode=$?
if [[ "$exitCode" =~ (0) ]]
then
  echo "[SUCCESS] Build has been verified to be reproducible."
else
  echo "[FAILURE] Build was not reproducible check diffoscope output above."
fi
exit $exitCode


