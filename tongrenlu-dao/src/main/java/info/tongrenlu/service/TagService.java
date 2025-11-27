package info.tongrenlu.service;


import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import info.tongrenlu.domain.TagBean;
import info.tongrenlu.mapper.TagMapper;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TagService extends ServiceImpl<TagMapper, TagBean> {

    public List<TagBean> getTagListByType(String type) {
        LambdaQueryWrapper<TagBean> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(TagBean::getType, type);
        return this.list(queryWrapper);
    }

}
