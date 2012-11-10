package org.scalaconsole
package net

import org.apache.ivy._
import org.apache.ivy.core.settings._
import org.apache.ivy.core.module.descriptor._
import org.apache.ivy.core.module.id._
import org.apache.ivy.plugins.parser.xml._
import org.apache.ivy.core.resolve._
import plugins.resolver._


object EmbeddedIvy {

  val repositories = ('central, "http://repo1.maven.org/maven2/") ::
    ('typesafe, "http://repo.typesafe.com/typesafe/releases/") ::
    Nil

  case class TransitiveResolver(m2Compatible: Boolean, name: String, patternRoot: String) extends IBiblioResolver {
    setM2compatible(m2Compatible)
    setName(name)
    setRoot(patternRoot)
  }

  def resolve(groupId: String, artifactId: String, version: String) = {
    //creates clear ivy settings
    val ivySettings = new IvySettings();
    //adding maven repo resolver
    //url resolver for configuration of maven repo
    val chainResolver = new ChainResolver
    for ((name, url) <- repositories) {
      chainResolver.add(TransitiveResolver(true, name.name, url))
    }
    ivySettings.addResolver(chainResolver)
    //set to the default resolver
    ivySettings.setDefaultResolver(chainResolver.getName)
    //creates an Ivy instance with settings
    val ivy = Ivy.newInstance(ivySettings)
    val md = DefaultModuleDescriptor.newCallerInstance(
      ModuleRevisionId.newInstance(groupId, artifactId, version), Array("*,!sources,!javadoc"), true, false
    )
    //init resolve report
    val options = new ResolveOptions
    val report = ivy.resolve(md, new ResolveOptions)
    //so you can get the jar library
    report.getAllArtifactsReports map (_.getLocalFile)
  }

  def resolveScala(version: String):List[java.io.File] = {
    for {lib <- ScalaCoreLibraries.toList
         file <- resolve("org.scala-lang", lib, version)} yield file
  }
}