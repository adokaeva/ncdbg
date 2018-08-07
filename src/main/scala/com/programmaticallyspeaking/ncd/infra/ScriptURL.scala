package com.programmaticallyspeaking.ncd.infra

import java.io.File
import java.net.{URI, URL}

/**
  * Represents a script URL. For file-based script URLs, we check equality and compute hash code via
  * [[java.io.File]], to get case-insensitive URL comparison on Windows.
  */
final class ScriptURL private[infra](private val uri: URI) {
  import ScriptURL._

  def toFile: File = new File(uri)

  def isFile: Boolean = uri.getScheme == "file"

  override def equals(other: Any): Boolean = other match {
    case that: ScriptURL if isFile && that.isFile => toFile == that.toFile
    case that: ScriptURL => uri == that.uri
    case _ => false
  }

  override def hashCode(): Int = {
    if (isFile) toFile.hashCode() else uri.hashCode()
  }

  override def toString: String = uri.toString

  def resolve(pathLike: String): ScriptURL = {
    if (looksLikeRelativePath(pathLike))
      new ScriptURL(uri.resolve(pathLike))
    else
      ScriptURL.create(pathLike)
  }
}

object ScriptURL {
  private[ScriptURL] def looksLikeRelativePath(x: String) =
    x.length > 0 && x(0) != '/' && !x.lift(1).contains(':')

  private def isAbsoluteUnixOrWindowsFilePath(x: String) =
    x.startsWith("/") || (x.lift(1).contains(':') && x.indexWhere(c => c == '\\' || c == '/') > 1)

  def create(url: URL): ScriptURL = new ScriptURL(url.toURI)

  def create(something: String): ScriptURL = {
    val uri = if (isAbsoluteUnixOrWindowsFilePath(something)) {
      val withUnixSlashes = something.replace("\\", "/")
      val uriPart = if (withUnixSlashes.startsWith("/")) withUnixSlashes else "/" + withUnixSlashes

      new URI("file", "", uriPart, null)
    } else if (something.startsWith("jar:")) {
      // Just drop the 'jar:' prefix. We keep the ! character so that the JAR file itself becomes
      // sort of a special folder.
      return create(something.substring(4))
    } else if (something.startsWith("file:") || something.startsWith("eval:")) {
      // Assume this is something resembling an URL already, e.g. file:/foo/bar,
      // but we don't know how many slashes there are.
      var (scheme, rest) = something.span(_ != ':')
      rest = rest.substring(1) // skip the leading :
      val slashCount = rest.prefixLength(_ == '/')
      new URI(scheme, "", "/" + rest.substring(slashCount), null)
    } else if (something.contains("..")) {
      throw new IllegalArgumentException(s"Cannot create ScriptURL from path/URL with '..' ($something)")
    } else {
      val withUnixSlashes = something.replace("\\", "/")
      new URI(withUnixSlashes)
    }
    normalized(new ScriptURL(uri))
  }

  private def normalized(url: ScriptURL): ScriptURL = {
    val normalized = url.uri.normalize()
    if (url.uri.getPath != normalized.getPath) {
      // normalization was necessary
      create(normalized.toString)
    } else url // no normalization necessary
  }
}

object FileScriptURL {
  def unapply(x: Any): Option[File] = x match {
    case s: ScriptURL if s.isFile => Some(s.toFile)
    case _ => None
  }
}