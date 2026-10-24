package com.shift28.app;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shift28.domain.VacationPeriod;
import com.shift28.persistence.VacationPeriodEntity;
import com.shift28.persistence.UserRepository;
import com.shift28.persistence.VacationPeriodRepository;

@Service
@Transactional
public class VacationService {

	private static final String STALE = "This vacation period was changed elsewhere. Reload and try again.";

	private final VacationPeriodRepository vacations;

	private final UserRepository users;

	VacationService(VacationPeriodRepository vacations, UserRepository users) {
		this.vacations = vacations;
		this.users = users;
	}

	@Transactional(readOnly = true)
	public List<VacationPeriodEntity> list(UUID userId) {
		return vacations.findByUserIdOrderByStartDate(userId);
	}

	public VacationPeriodEntity create(UUID userId, LocalDate startDate, LocalDate endDate, String label) {
		users.lockById(userId);
		VacationPeriod period = new VacationPeriod(startDate, endDate);
		return vacations.save(new VacationPeriodEntity(userId, period.start(), period.end(), label));
	}

	public VacationPeriodEntity update(UUID userId, UUID vacationId, LocalDate startDate, LocalDate endDate,
			String label, long version) {
		users.lockById(userId);
		VacationPeriodEntity vacation = find(userId, vacationId);
		if (vacation.getVersion() != version) {
			throw new StaleEditException(STALE);
		}
		VacationPeriod period = new VacationPeriod(startDate, endDate);
		vacation.update(period.start(), period.end(), label);
		return vacations.saveAndFlush(vacation);
	}

	public void delete(UUID userId, UUID vacationId) {
		users.lockById(userId);
		vacations.delete(find(userId, vacationId));
	}

	private VacationPeriodEntity find(UUID userId, UUID vacationId) {
		return vacations.findByIdAndUserId(vacationId, userId)
			.orElseThrow(() -> new NotFoundException("Vacation period not found."));
	}

}
