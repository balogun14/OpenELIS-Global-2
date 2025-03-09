package org.openelisglobal.common.provider.validation;

import java.util.HashSet;
import java.util.Set;
import org.openelisglobal.common.util.DateUtil;
import org.openelisglobal.internationalization.MessageUtil;
import org.openelisglobal.sample.service.SampleService;
import org.openelisglobal.spring.util.SpringContext;


public class NPANumAccessionGenerator implements IAccessionNumberGenerator {

    private static final String PREFIX = "BT"; // Fixed prefix
    private static final int PREFIX_LENGTH = PREFIX.length(); // 2
    private static final int YEAR_LENGTH = 2; // Two-digit year (YY)
    private static final int MAX_LENGTH = 20; // Total length (adjustable to 50)
    private static final int INCREMENT_LENGTH = MAX_LENGTH - PREFIX_LENGTH - YEAR_LENGTH; // 16 (or 46 for 50)
    private static final String INCREMENT_STARTING_VALUE = "1"; // Start at 1, padded later
    private static final long UPPER_INCREMENT_VALUE = (long) Math.pow(10, INCREMENT_LENGTH) - 1; // e.g., 9999999999999999 for 16 digits
    private static final boolean NEED_PROGRAM_CODE = false;
    private static final Set<String> REQUESTED_NUMBERS = new HashSet<>();
    private static final String INCREMENT_FORMAT = "%0" + INCREMENT_LENGTH + "d"; // Pads with leading zeros

    protected final SampleService sampleService = SpringContext.getBean(SampleService.class);

    public NPANumAccessionGenerator() {
        // Default constructor with fixed max length of 20
    }

    // IAccessionNumberGenerator methods
    @Override
    public boolean needProgramCode() {
        return NEED_PROGRAM_CODE;
    }

    public String createFirstAccessionNumber(String programCode) {
        return PREFIX + DateUtil.getTwoDigitYear() + String.format(INCREMENT_FORMAT, 1);
    }

    public String incrementAccessionNumber(String currentHighAccessionNumber) {
        String year = DateUtil.getTwoDigitYear();
        String currentYear = currentHighAccessionNumber.substring(PREFIX_LENGTH, PREFIX_LENGTH + YEAR_LENGTH);
        long increment = Long.parseLong(currentHighAccessionNumber.substring(PREFIX_LENGTH + YEAR_LENGTH));

        if (year.equals(currentYear)) {
            if (increment < UPPER_INCREMENT_VALUE) {
                increment++;
            } else {
                throw new IllegalArgumentException("AccessionNumber has no next value for year " + year);
            }
        } else {
            increment = Long.parseLong(INCREMENT_STARTING_VALUE);
        }

        return PREFIX + year + String.format(INCREMENT_FORMAT, increment);
    }

    @Override
    public String getNextAvailableAccessionNumber(String programCode, boolean reserve) {
        String nextAccessionNumber;
        String currentYearPrefix = PREFIX + DateUtil.getTwoDigitYear();
        String curLargestAccessionNumber = sampleService.getLargestAccessionNumberWithPrefix(currentYearPrefix);

        if (curLargestAccessionNumber == null) {
            nextAccessionNumber = createFirstAccessionNumber(programCode);
        } else {
            nextAccessionNumber = incrementAccessionNumber(curLargestAccessionNumber);
        }

        while (REQUESTED_NUMBERS.contains(nextAccessionNumber)) {
            nextAccessionNumber = incrementAccessionNumber(nextAccessionNumber);
        }

        if (reserve) {
            REQUESTED_NUMBERS.add(nextAccessionNumber);
        }

        return nextAccessionNumber;
    }

    @Override
    public String getNextAccessionNumber(String programCode, boolean reserve) {
        return getNextAvailableAccessionNumber(programCode, reserve);
    }

    @Override
    public int getMaxAccessionLength() {
        return MAX_LENGTH;
    }

    @Override
    public int getMinAccessionLength() {
        return MAX_LENGTH; // Fixed length for consistency
    }

    @Override
    public int getInvarientLength() {
        return PREFIX_LENGTH + YEAR_LENGTH; // BT + YY
    }

    @Override
    public int getChangeableLength() {
        return INCREMENT_LENGTH; // Numeric part
    }

    @Override
    public String getPrefix() {
        return PREFIX;
    }

    // IAccessionNumberValidator methods (minimal implementation, to be overridden by NPANumAccessionValidator)
    @Override
    public ValidationResults validFormat(String accessionNumber, boolean checkDate) throws IllegalArgumentException {
        // Minimal implementation; expect NPANumAccessionValidator to override
        if (accessionNumber == null || accessionNumber.length() != MAX_LENGTH) {
            return ValidationResults.LENGTH_FAIL;
        }
        return ValidationResults.SUCCESS; // Basic check, detailed validation in child class
    }

    @Override
    public String getInvalidMessage(ValidationResults results) {
        return MessageUtil.getMessage("sample.entry.invalid.accession.number");
    }

    @Override
    public String getInvalidFormatMessage(ValidationResults results) {
        return MessageUtil.getMessage("sample.entry.invalid.accession.number.format");
    }

    @Override
    public boolean accessionNumberIsUsed(String accessionNumber, String recordType) {
        return sampleService.getSampleByAccessionNumber(accessionNumber) != null;
    }

    @Override
    public ValidationResults checkAccessionNumberValidity(String accessionNumber, String recordType, String isRequired,
                                                          String projectFormName) {
        if (Boolean.parseBoolean(isRequired) && (accessionNumber == null || accessionNumber.trim().isEmpty())) {
            return ValidationResults.REQUIRED_FAIL;
        }
        if (accessionNumber == null || accessionNumber.trim().isEmpty()) {
            return ValidationResults.SUCCESS;
        }
        return validFormat(accessionNumber, true);
    }
}