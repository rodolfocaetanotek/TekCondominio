package br.com.tekcondo.assembly;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import br.com.tekcondo.assembly.AssemblyEntities.Attendance;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AttendanceRepository extends JpaRepository<Attendance, UUID> {

    List<Attendance> findByAssemblyIdOrderByRegisteredAtAsc(UUID assemblyId);

    Optional<Attendance> findByAssemblyIdAndUnitId(UUID assemblyId, UUID unitId);
}
