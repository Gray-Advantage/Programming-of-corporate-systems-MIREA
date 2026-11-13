package space.grayt.teremok.catalog.library.repository;

import java.util.*;
import org.springframework.data.jpa.repository.*;
import space.grayt.teremok.catalog.library.domain.LibraryWork;

public interface Works
  extends
    JpaRepository<LibraryWork, UUID>,
    JpaSpecificationExecutor<LibraryWork>
{
  Optional<LibraryWork> findByCode(String code);
}
