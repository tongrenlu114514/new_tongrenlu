package info.tongrenlu.service;


import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import info.tongrenlu.domain.ArticleBean;
import info.tongrenlu.mapper.ArticleMapper;
import org.springframework.stereotype.Service;

@Service
public class ArticleService extends ServiceImpl<ArticleMapper, ArticleBean> {
}
