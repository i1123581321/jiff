module sputnik.jiff {
  requires info.picocli;
  requires com.google.common;

  opens sputnik.jiff to
      info.picocli;
}
