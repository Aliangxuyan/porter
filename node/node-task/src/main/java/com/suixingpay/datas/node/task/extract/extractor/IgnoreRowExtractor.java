/**
 * All rights Reserved, Designed By Suixingpay.
 *
 * @author: zhangkewei[zhang_kw@suixingpay.com]
 * @date: 2017年12月27日 10:59
 * @Copyright ©2017 Suixingpay. All rights reserved.
 * 注意：本内容仅限于随行付支付有限公司内部传阅，禁止外泄以及用于其他的商业用途。
 */
package com.suixingpay.datas.node.task.extract.extractor;

import com.alibaba.fastjson.JSON;
import com.suixingpay.datas.node.core.event.etl.ETLBucket;
import com.suixingpay.datas.node.core.event.etl.ETLRow;
import com.suixingpay.datas.node.core.event.s.EventType;
import com.suixingpay.datas.node.task.extract.ExtractMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * 包含所有的忽略规则
 * @author: zhangkewei[zhang_kw@suixingpay.com]
 * @date: 2017年12月27日 10:59
 * @version: V1.0
 * @review: zhangkewei[zhang_kw@suixingpay.com]/2017年12月27日 10:59
 */
public class IgnoreRowExtractor implements Extractor {
    private  static final Logger LOGGER = LoggerFactory.getLogger(IgnoreRowExtractor.class);

    @Override
    public void extract(ETLBucket bucket, ExtractMetadata metadata) {
        long initBucketSize = bucket.getRows().size();
        List<ETLRow> removals = new ArrayList<>();
        for (ETLRow row : bucket.getRows()) {
            LOGGER.debug("trying extract row:{}", JSON.toJSONString(row));

            //包含、不包含表判断
            if (!metadata.getIncludeTables().isEmpty()) {
                String strSeg = new StringBuffer().append(row.getFinalSchema()).append(".")
                        .append(row.getFinalTable()).toString().intern();

                if (!metadata.getIncludeTables().contains(strSeg)) removals.add(row);

            } else if (metadata.getIncludeTables().isEmpty() && !metadata.getExcludeTables().isEmpty()) { //不包含表
                String strSeg = new StringBuffer().append(row.getFinalSchema()).append(".")
                        .append(row.getFinalTable()).toString().intern();

                if (metadata.getExcludeTables().contains(strSeg)) removals.add(row);
            }

            //当前仅支持插入、更新、删除、截断表
            if (row.getFinalOpType() == EventType.INSERT || row.getFinalOpType() == EventType.UPDATE
                    || row.getFinalOpType() == EventType.DELETE || row.getFinalOpType() == EventType.TRUNCATE) {
                //插入、删除、更新字段为空
                if ((null == row.getColumns() || row.getColumns().isEmpty()) && row.getFinalOpType() != EventType.TRUNCATE) {
                    LOGGER.debug("removing row:{}", JSON.toJSONString(row));
                    removals.add(row);
                }
            } else {
                LOGGER.debug("removing row:{}", JSON.toJSONString(row));
                removals.add(row);
            }
        }
        bucket.getRows().removeAll(removals);
        LOGGER.debug("sequence:{},before bucketSize:{},after bucketSize:{}", bucket.getSequence(), initBucketSize, bucket.getRows().size());
    }
}
