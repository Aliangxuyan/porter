/**
 * All rights Reserved, Designed By Suixingpay.
 *
 * @author: zhangkewei[zhang_kw@suixingpay.com]
 * @date: 2018年02月04日 11:57
 * @Copyright ©2018 Suixingpay. All rights reserved.
 * 注意：本内容仅限于随行付支付有限公司内部传阅，禁止外泄以及用于其他的商业用途。
 */

package com.suixingpay.datas.node.plugin.loader.kudu;

import com.suixingpay.datas.common.client.impl.KUDUClient;
import com.suixingpay.datas.common.dic.LoaderPlugin;
import com.suixingpay.datas.common.exception.TaskDataException;
import com.suixingpay.datas.common.exception.TaskStopTriggerException;
import com.suixingpay.datas.node.core.event.etl.ETLBucket;
import com.suixingpay.datas.node.core.event.etl.ETLColumn;
import com.suixingpay.datas.node.core.event.etl.ETLRow;
import com.suixingpay.datas.node.core.event.s.EventType;
import com.suixingpay.datas.node.core.loader.AbstractDataLoader;
import com.suixingpay.datas.node.core.loader.SubmitStatObject;
import lombok.SneakyThrows;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.ImmutableTriple;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

/**
 * @author: zhangkewei[zhang_kw@suixingpay.com]
 * @date: 2018年02月04日 11:57
 * @version: V1.0
 * @review: zhangkewei[zhang_kw@suixingpay.com]/2018年02月04日 11:57
 */
public class KuduLoader extends AbstractDataLoader {

    @Override
    protected String getPluginName() {
        return LoaderPlugin.KUDU_SINGLE.getCode();
    }

    @Override
    public Pair<Boolean, List<SubmitStatObject>> load(ETLBucket bucket) throws TaskStopTriggerException {
        List<SubmitStatObject> affectRow = new ArrayList<>();
        KUDUClient client = getLoadClient();
        bucket.getBatchRows().forEach(new Consumer<List<ETLRow>>() {
            @Override
            @SneakyThrows(TaskStopTriggerException.class)
            public void accept(List<ETLRow> l) {
                if (l.isEmpty()) return;
                //批次操作类型
                EventType type = l.get(0).getFinalOpType();
                String tableName = l.get(0).getFinalTable();
                String schemaName = l.get(0).getFinalSchema();

                String finalTableName = l.get(0).getFinalTable();
                //所有字段
                List<List<Triple<String, Integer, String>>> rows = new ArrayList<>();
                //主键字段
                List<List<Triple<String, Integer, String>>> keyRows = new ArrayList<>();
                l.forEach(r -> {
                    List<Triple<String, Integer, String>> row = new ArrayList<>();
                    row.addAll(KuduCustomETLRowField.getKeys(r));
                    row.addAll(KuduCustomETLRowField.getColumns(r));
                    rows.add(row);
                    keyRows.add(KuduCustomETLRowField.getKeys(r));
                });
                int[] result = new int[0];
                try {
                    switch (type) {
                        case INSERT:
                            result = client.insert(finalTableName, rows);
                            break;
                        case UPDATE:
                            result = new int[l.size()];
                            for (int i = 0; i < l.size(); i++) {
                                ETLRow r = l.get(i);
                                //如果主键存在变更
                                if (KuduCustomETLRowField.isKeyChanged(r)) {
                                    client.delete(finalTableName, Arrays.asList(KuduCustomETLRowField.getKeys(r)));
                                    //主键+非主键
                                    List<Triple<String, Integer, String>> row = new ArrayList<>();
                                    row.addAll(KuduCustomETLRowField.getKeys(r));
                                    row.addAll(KuduCustomETLRowField.getColumns(r));
                                    result = client.insert(finalTableName, Arrays.asList(row));
                                } else {
                                    List<Triple<String, Integer, String>> row = new ArrayList<>();
                                    row.addAll(KuduCustomETLRowField.getKeys(r));
                                    row.addAll(KuduCustomETLRowField.getColumns(r));
                                    result = client.update(finalTableName, Arrays.asList(row));
                                }
                            }
                            break;
                        case DELETE:
                            result = client.delete(finalTableName, keyRows);
                            break;
                        case TRUNCATE:
                            result = client.truncate(finalTableName);
                            break;
                        default:

                    }

                    //更新进度信息
                    for (int affect = 0; affect < l.size(); affect++) {
                        ETLRow row = l.get(affect);
                        affectRow.add(new SubmitStatObject(schemaName, tableName, type,
                                result[affect], row.getPosition(), row.getOpTime()));
                    }

                } catch (Exception e) {
                    throw new TaskStopTriggerException(e);
                }
            }
        });
        return new ImmutablePair(Boolean.TRUE, affectRow);
    }

    @Override
    public void mouldRow(ETLRow row) throws TaskDataException {
        if (null != row.getColumns()) {
            List<Triple<String, Integer, String>> keys = KuduCustomETLRowField.getKeys(row);
            List<Triple<String, Integer, String>> columns = KuduCustomETLRowField.getColumns(row);
            for (ETLColumn c : row.getColumns()) {
                if (c.isKey()) {
                    keys.add(new ImmutableTriple<>(c.getFinalName(), c.getFinalType(), c.getFinalValue()));
                    //更新时需要，判断主键是否发生变化
                    if (!c.getFinalOldValue().equals(c.getFinalValue()) && row.getFinalOpType() == EventType.UPDATE) {
                        KuduCustomETLRowField.setKeyChanged(row, true);
                    }
                } else {
                    columns.add(new ImmutableTriple<>(c.getFinalName(), c.getFinalType(), c.getFinalValue()));
                }
            }
        }
    }

    /**
     * 自定义扩展字段
     */
    protected static class KuduCustomETLRowField {

        private  static String KEY_FIELD = "kuduKey";
        private  static String COLUMN_FIELD = "kuduColumn";
        private  static String IS_KEY_CHANGED_FIELD = "kuduKeyChanged";

        protected static List<Triple<String, Integer, String>> getKeys(ETLRow row) {
            return (List<Triple<String, Integer, String>>) row.getExtendsField().computeIfAbsent(KEY_FIELD, k -> new ArrayList<>());
        }

        protected static List<Triple<String, Integer, String>> getColumns(ETLRow row) {
            return (List<Triple<String, Integer, String>>) row.getExtendsField().computeIfAbsent(COLUMN_FIELD, k -> new ArrayList<>());
        }

        protected static Boolean isKeyChanged(ETLRow row) {
            return (Boolean) row.getExtendsField().computeIfAbsent(IS_KEY_CHANGED_FIELD, k -> Boolean.FALSE);
        }

        protected static Boolean setKeyChanged(ETLRow row, Boolean isChanged) {
            return (Boolean) row.getExtendsField().put(IS_KEY_CHANGED_FIELD, isChanged);
        }
    }

}
