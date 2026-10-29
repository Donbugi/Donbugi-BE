package hongik.finEdu.main.weather;

import hongik.finEdu.main.weather.entity.EconomicWeatherHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface EconomicWeatherHistoryRepository extends JpaRepository<EconomicWeatherHistory, Long> {

    Optional<EconomicWeatherHistory> findByWeatherDate(LocalDate date);

    List<EconomicWeatherHistory> findByWeatherDateBetweenOrderByWeatherDateAsc(LocalDate from, LocalDate to);
}
