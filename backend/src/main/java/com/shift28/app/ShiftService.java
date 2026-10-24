package com.shift28.app;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shift28.domain.Shift;
import com.shift28.domain.Status;
import com.shift28.persistence.ShiftEntity;
import com.shift28.persistence.ShiftRepository;
import com.shift28.persistence.UserRepository;

@Service
@Transactional
public class ShiftService {

	private static final String OVERLAP = "This shift overlaps another shift.";

	private static final String STALE = "This shift was changed elsewhere. Reload and try again.";

	private final ShiftRepository shifts;

	private final UserRepository users;

	private final ComplianceService compliance;

	private final Clock clock;

	ShiftService(ShiftRepository shifts, UserRepository users, ComplianceService compliance, Clock clock) {
		this.shifts = shifts;
		this.users = users;
		this.compliance = compliance;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public List<ShiftEntity> list(UUID userId, LocalDate from, LocalDate to) {
		return shifts.findOverlapping(userId, ComplianceService.startOf(from),
				ComplianceService.startOf(to.plusDays(1)));
	}

	public Saved create(UUID userId, ShiftInput input, boolean confirmed) {
		users.lockById(userId);
		DraftCheck check = verify(userId, input.toShift(null), null, confirmed);
		ShiftEntity shift = new ShiftEntity(userId, input.jobId(), input.startAt(), input.endAt(), input.breakStart(),
				input.breakEnd(), input.note());
		return new Saved(save(shift), check);
	}

	public Saved update(UUID userId, UUID shiftId, ShiftInput input, long version, boolean confirmed) {
		users.lockById(userId);
		ShiftEntity shift = find(userId, shiftId);
		if (shift.getVersion() != version) {
			throw new StaleEditException(STALE);
		}
		DraftCheck check = verify(userId, input.toShift(shiftId), shiftId, confirmed);
		shift.update(input.jobId(), input.startAt(), input.endAt(), input.breakStart(), input.breakEnd(), input.note());
		return new Saved(save(shift), check);
	}

	public void delete(UUID userId, UUID shiftId) {
		users.lockById(userId);
		shifts.delete(find(userId, shiftId));
	}

	private DraftCheck verify(UUID userId, Shift draft, UUID excludeShiftId, boolean confirmed) {
		DraftCheck check = compliance.check(userId, draft, excludeShiftId);
		if (!check.overlaps().isEmpty()) {
			throw new ConflictException(OVERLAP);
		}
		if (check.status() != Status.OK && !confirmed && draft.endAt().isAfter(clock.instant())) {
			throw new ConfirmationRequiredException(check);
		}
		return check;
	}

	private ShiftEntity save(ShiftEntity shift) {
		try {
			return shifts.saveAndFlush(shift);
		}
		catch (DataIntegrityViolationException e) {
			if (DatabaseErrors.hasSqlState(e, DatabaseErrors.EXCLUSION_VIOLATION)) {
				throw new ConflictException(OVERLAP);
			}
			throw e;
		}
	}

	private ShiftEntity find(UUID userId, UUID shiftId) {
		return shifts.findByIdAndUserId(shiftId, userId).orElseThrow(() -> new NotFoundException("Shift not found."));
	}

	public record Saved(ShiftEntity shift, DraftCheck check) {
	}

}
