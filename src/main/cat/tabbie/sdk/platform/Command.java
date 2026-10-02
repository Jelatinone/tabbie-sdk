package cat.tabbie.sdk.platform;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import lombok.NonNull;

public record Command(@NonNull Argument executable, @NonNull List<Token> tokens) {

  public Command {
    tokens = List.copyOf(tokens);
  }

  public List<String> render(@NonNull Function<Relative, Path> resolver) {
    List<String> line = new ArrayList<>();
    line.add(executable.render(resolver));
    tokens.forEach(token -> line.addAll(token.expand(resolver)));
    return List.copyOf(line);
  }

  public sealed interface Token permits Argument, Option {

    @NonNull
    List<String> expand(@NonNull Function<Relative, Path> resolver);
  }

  public sealed interface Argument extends Token {

    @NonNull
    String render(@NonNull Function<Relative, Path> resolver);

    @Override
    default List<String> expand(Function<Relative, Path> resolver) {
      return List.of(render(resolver));
    }

    record Literal(@NonNull String value) implements Argument {
      public String render(Function<Relative, Path> resolver) {
        return value;
      }
    }

    record Location(@NonNull Relative path) implements Argument {
      public String render(Function<Relative, Path> resolver) {
        return resolver.apply(path).toString();
      }
    }
  }

  public record Option(@NonNull String name, @NonNull Argument value, @NonNull Style style) implements Token {

    public Option {
      if (name.isBlank()) {
        throw new IllegalArgumentException("An option needs a name");
      }
    }

    public enum Style {

      /**
       * {@code --port 25565}
       */
      SEPARATE,
      /**
       * {@code --port=25565}, {@code -Dkey=value}
       */
      EQUALS,
      /**
       * {@code -Xmx4G}
       */
      JOINED
    }

    @Override
    public List<String> expand(Function<Relative, Path> resolver) {
      String rendered = value.render(resolver);
      return switch (style) {
        case SEPARATE -> List.of(name, rendered);
        case EQUALS -> List.of(name + "=" + rendered);
        case JOINED -> List.of(name + rendered);
      };
    }
  }
}
