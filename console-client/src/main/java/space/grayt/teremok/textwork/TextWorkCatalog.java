package space.grayt.teremok.textwork;

import java.util.List;
import java.util.Optional;
import space.grayt.teremok.domain.TextWork;

public interface TextWorkCatalog {

    List<TextWork> all();

    Optional<TextWork> find(String textWorkId);

    List<String> warnings();
}
