package com.shtrih.tinyjavapostester;

import com.shtrih.barcode.PrinterBarcode;
import com.shtrih.fiscalprinter.ShtrihFiscalPrinter;
import com.shtrih.fiscalprinter.SmFiscalPrinterException;
import com.shtrih.fiscalprinter.command.DeviceMetrics;
import com.shtrih.fiscalprinter.command.FSDocType;
import com.shtrih.fiscalprinter.command.FSStatusInfo;
import com.shtrih.fiscalprinter.command.LongPrinterStatus;
import com.shtrih.jpos.fiscalprinter.JposExceptionHandler;
import com.shtrih.jpos.fiscalprinter.SmFptrConst;
import com.shtrih.jpos1c.xml.check.Barcode;
import com.shtrih.jpos1c.xml.check.CheckPackage;
import com.shtrih.jpos1c.xml.check.FiscalString;
import com.shtrih.jpos1c.xml.check.TextString;
import com.shtrih.jpos1c.xml.correctioncheck.CheckCorrectionPackage;
import com.shtrih.jpos1c.xml.inputparameters.InputParameters;

import jpos.JposException;

/**
 * Класс демонстрирует универсальный код для формирования документо в соответствии с ФФД 1.05
 */
public class FFD105Documents {

    /**
     * Открытие смены
     *
     * @param printer         драйвер
     * @param inputParameters сведения о кассире
     */
    public void openShift(ShtrihFiscalPrinter printer, InputParameters inputParameters) throws Exception {
        prepare(printer);

        FSStatusInfo fsStatus = printer.fsReadStatus();
        int docNumebr = (int) fsStatus.getDocNumber() + 1; // Номер ФД
        LongPrinterStatus printerStatus = printer.readLongPrinterStatus();
        int shiftNumber = printerStatus.getCurrentShiftNumber(); // Номер смены

        if (printerStatus.getPrinterMode().isDayOpened()) {
            throw JposExceptionHandler.getJposException(
                    new SmFiscalPrinterException(2, "Открытая смена, операция невозможна"));
        }

        // 1021, кассир
        printer.writeCashierName(inputParameters.Parameters.CashierName);

        printer.fsStartDayOpen();

        // ИНН кассира
        writeVATINTagIfNotNullAndNotEmpty(printer, 1203, inputParameters.Parameters.CashierVATIN);

        printer.openFiscalDay();
    }

    /**
     * Закрытие смены
     *
     * @param printer         драйвер
     * @param inputParameters сведения о кассире
     */
    public void closeShift(ShtrihFiscalPrinter printer, InputParameters inputParameters) throws Exception {
        prepare(printer);

        FSStatusInfo fsStatus = printer.fsReadStatus();
        int docNumebr = (int) fsStatus.getDocNumber() + 1; // Номер ФД
        LongPrinterStatus printerStatus = printer.readLongPrinterStatus();
        int shiftNumber = printerStatus.getCurrentShiftNumber(); // Номер смены

        if (printerStatus.getPrinterMode().isDayClosed()) {
            throw JposExceptionHandler.getJposException(
                    new SmFiscalPrinterException(2, "Закрытая смена, операция невозможна"));
        }

        // 1021, кассир
        printer.writeCashierName(inputParameters.Parameters.CashierName);

        printer.fsStartDayClose();

        // ИНН кассира
        writeVATINTagIfNotNullAndNotEmpty(printer, 1203, inputParameters.Parameters.CashierVATIN);

        printer.printZReport();
    }

    /**
     * Формирование отчета о текущем состоянии расчетов
     *
     * @param printer         драйвер
     * @param inputParameters сведения о кассире
     */
    public void reportCurrentStatusOfSettlements(ShtrihFiscalPrinter printer, InputParameters inputParameters) throws Exception {

        prepare(printer);

        // Имя кассира которое будет напечатано на чеке, в ФД оно не передается
        printer.writeCashierName(inputParameters.Parameters.CashierName);

        printer.fsPrintCalcReport();
    }

    /**
     * Формирование чека коррекции
     *
     * @param printer        драйвер
     * @param params         данные чека
     */
    public void processCorrectionCheck(ShtrihFiscalPrinter printer, CheckCorrectionPackage params) throws Exception {
        prepare(printer);

        // 1021, кассир
        printer.writeCashierName(params.Parameters.CashierName);

        LongPrinterStatus printerStatus = printer.readLongPrinterStatus();

        if (printerStatus.getPrinterMode().isDayClosed()) {
            throw JposExceptionHandler.getJposException(
                    new SmFiscalPrinterException(2, "Закрытая смена, операция невозможна"));
        }

        printer.fsStartCorrectionReceipt();

        // ИНН кассира
        writeVATINTagIfNotNullAndNotEmpty(printer, 1203, params.Parameters.CashierVATIN);

        // 2. Записываем составные части тэга 1174
        // 1177, описание коррекции
        writeTagIfNotNullAndNotEmpty(printer, 1177, params.Parameters.CorrectionBaseName);

        // 1178, дата документа основания для коррекции UnixTime
        if (params.Parameters.CorrectionBaseDate != null && !params.Parameters.CorrectionBaseDate.isEmpty()) {
            long correctionBaseDate = params.Parameters.getCorrectionBaseDate();
            printer.fsWriteTag(1178, correctionBaseDate, 4);
        }

        // 1179, номер документа основания для коррекции
        writeTagIfNotNullAndNotEmpty(printer, 1179, params.Parameters.CorrectionBaseNumber);

        Object[] outParams = new Object[3];
        printer.fsPrintCorrectionReceipt3(
                params.Parameters.CorrectionType,
                params.Parameters.PaymentType,
                params.Parameters.getSum(),
                params.Payments.getCash(),
                params.Payments.getElectronicPayment(),
                params.Payments.getAdvancePayment(),
                params.Payments.getCredit(),
                params.Payments.getCashProvision(),
                params.Parameters.getSumTAX18(),
                params.Parameters.getSumTAX10(),
                params.Parameters.getSumTAX0(),
                params.Parameters.getSumTAXNone(),
                params.Parameters.getSumTAX118(),
                params.Parameters.getSumTAX110(),
                params.Parameters.getTaxVariant(),
                outParams);

        //setCheckNumber((Integer) outParams[1]); // Номер ФД
        //setSessionNumber(printerStatus.getCurrentShiftNumber()); // Номер смены
        //setFiscalSign((Long) outParams[2]); // ФП
    }


    /**
     * Формирование чека
     *
     * @param printer        драйвер
     * @param params         данные чека
     * @param electronically Формирование чека в только электроном виде. Печать чека не осуществляется.
     */
    public void processCheck(ShtrihFiscalPrinter printer, CheckPackage params, boolean electronically) throws Exception {

        prepare(printer);

        LongPrinterStatus printerStatus = printer.readLongPrinterStatus();

        // данная проверка нужна, т.к. код начала чека автоматом открывает смену, при этом
        // в открытие смены не будет передан ИНН кассира
        if (printerStatus.getPrinterMode().isDayClosed()) {
            throw JposExceptionHandler.getJposException(
                    new SmFiscalPrinterException(2, "Закрытая смена, операция невозможна"));
        }

        if (isCashCore(printer)) {
            printer.writeTable(1, 1, 48, electronically ? "1" : "0");
        }

        // 1021, кассир
        printer.writeCashierName(params.Parameters.CashierName);

        // Задаем тип чека
        if (params.Parameters.PaymentType == 1) { // приход
            printer.setFiscalReceiptType(SmFptrConst.SMFPTR_RT_SALE);
        } else if (params.Parameters.PaymentType == 2) { // возврат прихода
            printer.setFiscalReceiptType(SmFptrConst.SMFPTR_RT_RETSALE);
        } else if (params.Parameters.PaymentType == 3) { // расход
            printer.setFiscalReceiptType(SmFptrConst.SMFPTR_RT_BUY);
        } else if (params.Parameters.PaymentType == 4) { // возврат расхода
            printer.setFiscalReceiptType(SmFptrConst.SMFPTR_RT_RETBUY);
        } else {
            throw new UnsupportedOperationException("Неизвестный тип чека " + params.Parameters.PaymentType);
        }

        // Указываем систему налогообложения
        printer.setParameter(SmFptrConst.SMFPTR_DIO_PARAM_TAX_SYSTEM, params.Parameters.getTaxVariant());

        printer.beginFiscalReceipt(false);

        FSStatusInfo fsStatus = printer.fsReadStatus();
        int docNumebr = (int) fsStatus.getDocNumber() + 1; // Номер ФД
        int shiftNumber = printer.readLongPrinterStatus().getCurrentShiftNumber(); // Номер смены

        if (electronically) {
            if (isDesktop(printer)) {
                printer.writeTable(17, 1, 7, "1");
            }
        }

        for (Object element : params.Positions.FiscalStrings) {

            if (element instanceof FiscalString) {

                FiscalString item = (FiscalString) element;

                // Позиция с признаком способа расчета и признаком предмета расчета
                // 1214, признак способа расчета, если не указывать будет 0
                // ВНИМАНИЕ: значение сохраняется после вызова printRecItem
                printer.setParameter(SmFptrConst.SMFPTR_DIO_PARAM_ITEM_PAYMENT_TYPE, item.SignMethodCalculation);
                // 1212, признак предмета расчета, если не указывать будет 0
                // ВНИМАНИЕ: значение сохраняется после вызова printRecItem
                printer.setParameter(SmFptrConst.SMFPTR_DIO_PARAM_ITEM_SUBJECT_TYPE, item.SignCalculationObject);

                // Позиция с коррекцией на +-1 копейку
                // Сумма позиции будет сброшена драйвером после вызова printRecItem
                printer.setParameter(SmFptrConst.SMFPTR_DIO_PARAM_ITEM_TOTAL_AMOUNT, (int) item.getAmount());
                printer.setDepartment(item.Department);
                printer.printRecItem(item.Name, 0, (int) item.getQuantity(), item.getTax(), item.getPrice(), "");

                // TODO: Agent and Purveyor tags?

            } else if (element instanceof TextString) {
                TextString item = (TextString) element;
                printer.printRecMessage(item.Text, item.FontNumber);
            } else if (element instanceof Barcode) {
                Barcode item = (Barcode) element;
                printBarCode(printer, item);
            } else {
                throw new UnsupportedOperationException("Unknown element of type " + element.getClass().getName());
            }
        }

        // ИНН кассира
        writeVATINTagIfNotNullAndNotEmpty(printer, 1203, params.Parameters.CashierVATIN);

        // телефон или электронный адрес покупателя, не могут быть одновременно заданы
        writeTagIfNotNullAndNotEmpty(printer, 1008, params.Parameters.CustomerEmail);
        writeTagIfNotNullAndNotEmpty(printer, 1008, params.Parameters.CustomerPhone);

        // адрес электронной почты отправителя чека
        writeTagIfNotNullAndNotEmpty(printer, 1117, params.Parameters.SenderEmail);

        // адрес расчетов
        // (51) Некорректные параметры в команде
        // writeTagIfNotNullAndNotEmpty(printer, 1009, params.Parameters.AddressSettle);

        // место расчетов
        // (51) Некорректные параметры в команде
        // writeTagIfNotNullAndNotEmpty(printer, 1187, params.Parameters.PlaceSettle);

        // признак агента
        if (params.Parameters.AgentSign > 0)
            printer.fsWriteTag(1057, params.Parameters.AgentSign, 1);

        // операция платежного агента
        writeTagIfNotNullAndNotEmpty(printer, 1044, params.Parameters.AgentData.PayingAgentOperation);
        // телефон платежного агента
        writeTagIfNotNullAndNotEmpty(printer, 1073, params.Parameters.AgentData.PayingAgentPhone);
        // телефон оператора по приему платежей
        writeTagIfNotNullAndNotEmpty(printer, 1074, params.Parameters.AgentData.ReceivePaymentsOperatorPhone);
        // телефон оператора перевода
        writeTagIfNotNullAndNotEmpty(printer, 1075, params.Parameters.AgentData.MoneyTransferOperatorPhone);
        // наименование оператора перевода
        writeTagIfNotNullAndNotEmpty(printer, 1026, params.Parameters.AgentData.MoneyTransferOperatorName);
        // адрес оператора перевода
        writeTagIfNotNullAndNotEmpty(printer, 1005, params.Parameters.AgentData.MoneyTransferOperatorAddress);
        // ИНН оператора перевода
        writeVATINTagIfNotNullAndNotEmpty(printer, 1016, params.Parameters.AgentData.MoneyTransferOperatorVATIN);

        // телефон поставщика
        writeVATINTagIfNotNullAndNotEmpty(printer, 1171, params.Parameters.PurveyorData.PurveyorPhone);

        if (params.Payments.getCash() > 0)
            printer.printRecTotal(0, params.Payments.getCash(), "0");
        if (params.Payments.getElectronicPayment() > 0)
            printer.printRecTotal(0, params.Payments.getElectronicPayment(), "1");
        if (params.Payments.getAdvancePayment() > 0)
            printer.printRecTotal(0, params.Payments.getAdvancePayment(), "13");
        if (params.Payments.getCredit() > 0)
            printer.printRecTotal(0, params.Payments.getCredit(), "14");
        if (params.Payments.getCashProvision() > 0)
            printer.printRecTotal(0, params.Payments.getCashProvision(), "15");

        printer.endFiscalReceipt(false);
    }

    private void printBarCode(ShtrihFiscalPrinter printer, Barcode item) throws JposException {
        if (item.BarcodeType.equals("EAN13")) {
            PrinterBarcode barcode = new PrinterBarcode();
            barcode.setText(item.Barcode);
            barcode.setType(SmFptrConst.SMFPTR_BARCODE_EAN13);
            barcode.setPrintType(SmFptrConst.SMFPTR_PRINTTYPE_DRIVER);
            barcode.setHeight(100);
            barcode.setBarWidth(3);
            printer.printBarcode(barcode);
        } else if (item.BarcodeType.equals("EAN8")) {
            PrinterBarcode barcode = new PrinterBarcode();
            barcode.setText(item.Barcode);
            barcode.setType(SmFptrConst.SMFPTR_BARCODE_EAN8);
            barcode.setPrintType(SmFptrConst.SMFPTR_PRINTTYPE_DRIVER);
            barcode.setHeight(100);
            barcode.setBarWidth(3);
            printer.printBarcode(barcode);
        } else if (item.BarcodeType.equals("CODE39")) {
            PrinterBarcode barcode = new PrinterBarcode();
            barcode.setText(item.Barcode);
            barcode.setType(SmFptrConst.SMFPTR_BARCODE_CODE39);
            barcode.setPrintType(SmFptrConst.SMFPTR_PRINTTYPE_DRIVER);
            barcode.setHeight(100);
            barcode.setBarWidth(2);
            printer.printBarcode(barcode);
        } else if (item.BarcodeType.equals("QR")) {
            PrinterBarcode barcode = new PrinterBarcode();
            barcode.setTextFont(1);
            barcode.setTextPosition(SmFptrConst.SMFPTR_TEXTPOS_NOTPRINTED);
            barcode.setBarWidth(5);
            barcode.setHeight(100);
            barcode.setPrintType(SmFptrConst.SMFPTR_PRINTTYPE_DRIVER);
            barcode.setText(item.Barcode);
            barcode.setType(SmFptrConst.SMFPTR_BARCODE_QR_CODE);
            printer.printBarcode(barcode);
        } else {
            throw new UnsupportedOperationException("Неподдерживаемый тип ШК " + item.BarcodeType);
        }
    }

    private void writeTagIfNotNullAndNotEmpty(ShtrihFiscalPrinter printer, int tagId, String value) throws Exception {
        if (value != null && !value.isEmpty())
            printer.fsWriteTag(tagId, value);
    }

    private void writeVATINTagIfNotNullAndNotEmpty(ShtrihFiscalPrinter printer, int tagId, String value) throws Exception {
        if (value != null && !value.isEmpty())
            printer.fsWriteTag(tagId, formatInn(value));
    }

    private String formatInn(String inn) {
        // ИНН должно быть дополнено пробелами до 12 символов
        return extendWithSpacesToFixedLength(inn, 12);
    }

    private String extendWithSpacesToFixedLength(String value, int length) {
        if (value.length() >= length)
            return value;

        return value + new String(new char[length - value.length()]).replace('\0', ' ');
    }

    private void prepare(ShtrihFiscalPrinter printer) throws JposException {
        LongPrinterStatus status = printer.readLongPrinterStatus();

        // проверяем наличие бумаги
        if (status.getSubmode() == 1 || status.getSubmode() == 2) {
            final int errorCodeNoPaper = 107;
            throw JposExceptionHandler.getJposException(
                    new SmFiscalPrinterException(errorCodeNoPaper, "Отсутствует бумага"));
        }

        // проверяем, есть ли открытый документ в ФН
        FSStatusInfo fsStatus = printer.fsReadStatus();

        if (fsStatus.getDocType().getValue() != FSDocType.FS_DOCTYPE_NONE)
            printer.fsCancelDocument(); // если есть отменяем

        printer.resetPrinter();
    }

    private boolean isDesktop(ShtrihFiscalPrinter printer) throws JposException {
        DeviceMetrics metrics = printer.readDeviceMetrics();
        return metrics.getModel() != 19 && // Штрих-МОБАЙЛ
                metrics.getModel() != 45;  // КЯ
    }

    private boolean isMobile(ShtrihFiscalPrinter printer) throws JposException {
        DeviceMetrics metrics = printer.readDeviceMetrics();
        return metrics.getModel() == 19; // Штрих-МОБАЙЛ
    }

    private boolean isCashCore(ShtrihFiscalPrinter printer) throws JposException {
        DeviceMetrics metrics = printer.readDeviceMetrics();
        return metrics.getModel() == 45; // КЯ
    }
}