/**
 * The contents of this file are subject to the Mozilla Public License Version 1.1 (the "License");
 * you may not use this file except in compliance with the License. You may obtain a copy of the
 * License at http://www.mozilla.org/MPL/
 *
 * <p>Software distributed under the License is distributed on an "AS IS" basis, WITHOUT WARRANTY OF
 * ANY KIND, either express or implied. See the License for the specific language governing rights
 * and limitations under the License.
 *
 * <p>The Original Code is OpenELIS code.
 *
 * <p>Copyright (C) The Minnesota Department of Health. All Rights Reserved.
 *
 * <p>Contributor(s): CIRG, University of Washington, Seattle WA.
 */
package org.openelisglobal.common.provider.validation;

import java.util.HashSet;
import java.util.Set;
import org.openelisglobal.common.util.ConfigurationProperties;
import org.openelisglobal.common.util.ConfigurationProperties.Property;
import org.openelisglobal.common.util.DateUtil;
import org.openelisglobal.internationalization.MessageUtil;
import org.openelisglobal.sample.service.SampleService;
import org.openelisglobal.sample.util.AccessionNumberUtil;
import org.openelisglobal.spring.util.SpringContext;

/**
 * Combined generator and validator for National Pathology Accession Numbers (NPA)
 * with format BTYYNNNNNNNNNNNNNNNN. BT is a fixed prefix, YY is the two-digit year,
 * and NNNNNNNNNNNNNNNN is a 16-digit numeric increment. Total length is 20 characters,
 * adjustable to 50.
 */
public class NPANumAccessionValidator extends NPANumAccessionGenerator implements IAccessionNumberValidator {

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

    private final SampleService sampleService = SpringContext.getBean(SampleService.class);

    public NPANumAccessionValidator() {
        super(); // Call parent constructor
    }

    // Generator methods inherited from NPANumAccessionGenerator
    @Override
    public boolean needProgramCode() {
        return NEED_PROGRAM_CODE;
    }

    @Override
    public String createFirstAccessionNumber(String programCode) {
        return PREFIX + DateUtil.getTwoDigitYear() + String.format(INCREMENT_FORMAT, 1);
    }

    @Override
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
    public String getNextAvailableAccessionNumber(String prefix, boolean reserve) {
        String nextAccessionNumber;
        String currentYearPrefix = PREFIX + DateUtil.getTwoDigitYear();
        String curLargestAccessionNumber = sampleService.getLargestAccessionNumberWithPrefix(currentYearPrefix);

        if (curLargestAccessionNumber == null) {
            nextAccessionNumber = createFirstAccessionNumber(null);
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

    // Validator methods from IAccessionNumberValidator
    @Override
    public ValidationResults validFormat(String accessionNumber, boolean checkDate) throws IllegalArgumentException {
        if (!Boolean.valueOf(ConfigurationProperties.getInstance().getPropertyValue(Property.ACCESSION_NUMBER_VALIDATE))) {
            return AccessionNumberUtil.containsBlackListCharacters(accessionNumber) ? ValidationResults.FORMAT_FAIL
                    : ValidationResults.SUCCESS;
        }

        if (accessionNumber == null || accessionNumber.length() != MAX_LENGTH) {
            return ValidationResults.LENGTH_FAIL;
        }

        if (!accessionNumber.startsWith(PREFIX)) {
            return ValidationResults.FORMAT_FAIL;
        }

        if (checkDate) {
            String year = accessionNumber.substring(PREFIX_LENGTH, PREFIX_LENGTH + YEAR_LENGTH);
            if (!DateUtil.getTwoDigitYear().equals(year)) {
                return ValidationResults.YEAR_FAIL;
            }
        }

        try {
            Long.parseLong(accessionNumber.substring(PREFIX_LENGTH + YEAR_LENGTH));
        } catch (NumberFormatException e) {
            return ValidationResults.FORMAT_FAIL;
        }

        return ValidationResults.SUCCESS;
    }

    @Override
    public String getInvalidMessage(ValidationResults results) {
        switch (results) {
            case LENGTH_FAIL:
                return MessageUtil.getMessage("sample.entry.invalid.accession.number.length");
            case USED_FAIL:
                return MessageUtil.getMessage("sample.entry.invalid.accession.number.used");
            case YEAR_FAIL:
            case FORMAT_FAIL:
                return getInvalidFormatMessage(results);
            case REQUIRED_FAIL:
                return MessageUtil.getMessage("sample.entry.invalid.accession.number.required");
            default:
                return MessageUtil.getMessage("sample.entry.invalid.accession.number");
        }
    }

    @Override
    public String getInvalidFormatMessage(ValidationResults results) {
        return MessageUtil.getMessage("sample.entry.invalid.accession.number.format.corrected",
                new String[] { getFormatPattern(), getFormatExample() });
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
    public boolean accessionNumberIsUsed(String accessionNumber, String recordType) {
        return sampleService.getSampleByAccessionNumber(accessionNumber) != null;
    }

    @Override
    public ValidationResults checkAccessionNumberValidity(String accessionNumber, String recordType, String isRequired,
                                                          String projectFormName) {
        if (isRequired != null && Boolean.parseBoolean(isRequired) && (accessionNumber == null || accessionNumber.trim().isEmpty())) {
            return ValidationResults.REQUIRED_FAIL;
        }

        if (accessionNumber == null || accessionNumber.trim().isEmpty()) {
            return ValidationResults.SUCCESS; // Not required and not provided
        }

        ValidationResults results = validFormat(accessionNumber, true);
        if (results == ValidationResults.SUCCESS && accessionNumberIsUsed(accessionNumber, recordType)) {
            results = ValidationResults.USED_FAIL;
        }
        return results;
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

    // Helper methods for format display
    private String getFormatPattern() {
        StringBuilder pattern = new StringBuilder(PREFIX);
        pattern.append(MessageUtil.getMessage("date.two.digit.year"));
        for (int i = 0; i < INCREMENT_LENGTH; i++) {
            pattern.append("#");
        }
        return pattern.toString();
    }

    private String getFormatExample() {
        StringBuilder example = new StringBuilder(PREFIX);
        example.append(DateUtil.getTwoDigitYear());
        example.append(String.format(INCREMENT_FORMAT, 1));
        return example.toString();
    }
}